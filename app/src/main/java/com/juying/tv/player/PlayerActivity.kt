package com.juying.tv.player

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.juying.tv.R
import com.juying.tv.data.local.AppDatabase
import com.juying.tv.data.local.HistoryEntity
import com.juying.tv.data.remote.Http
import com.juying.tv.data.source.ConfigState
import com.juying.tv.data.source.SourceManager
import com.juying.tv.ui.theme.Accent
import com.juying.tv.ui.theme.JyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 播放器：Media3 官方 StyledPlayerView 控制器（自动隐藏/进度条/快进退）
 * + 自定义按钮（选集/倍速/比例/上下集）+ Compose 手势层与选集抽屉
 */
class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SITE_KEY = "site_key"
        const val EXTRA_VOD_ID = "vod_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PIC = "pic"
        const val EXTRA_SOURCE_NAME = "source_name"
        const val EXTRA_SOURCE_INDEX = "source_index"
        const val EXTRA_EPISODE_INDEX = "episode_index"
        const val EXTRA_EP_NAMES = "ep_names"
        const val EXTRA_EP_URLS = "ep_urls"
        const val EXTRA_START_POSITION = "start_pos"
        const val EXTRA_LIVE = "live"
        const val EXTRA_LIVE_URL = "live_url"
        const val EXTRA_LIVE_URLS = "live_urls"
    }

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var sniffer: WebSniffer? = null
    private var dsFactory: OkHttpDataSource.Factory? = null
    private var playHeaders: Map<String, String> = emptyMap()
    private var sniffTriedFor: String? = null

    // 控制器视图引用
    private var titleView: TextView? = null
    private var episodeView: TextView? = null
    private var speedView: TextView? = null

    private val uiState = mutableStateOf(PlayerUiState())

    data class PlayerUiState(
        val buffering: Boolean = true,
        val sniffing: Boolean = false,
        val error: String? = null,
        val controlsVisible: Boolean = false,
        val episodesVisible: Boolean = false,
        val gestureHint: String? = null,
        val currentEp: Int = 0,
        val scaleMode: Int = 0,
    )

    private var epNames: List<String> = emptyList()
    private var epUrls: List<String> = emptyList()
    private var currentEp = 0
    private var isLive = false
    private var siteKey = ""
    private var vodId = ""
    private var pic = ""
    private var sourceName = ""
    private var sourceIndex = 0
    private var title = ""
    private var liveUrls: List<String> = emptyList()
    private var liveUrlIndex = 0
    private var bufferingSince = 0L
    private var speed = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUi()

        isLive = intent.getBooleanExtra(EXTRA_LIVE, false)
        siteKey = intent.getStringExtra(EXTRA_SITE_KEY) ?: ""
        vodId = intent.getStringExtra(EXTRA_VOD_ID) ?: ""
        pic = intent.getStringExtra(EXTRA_PIC) ?: ""
        sourceName = intent.getStringExtra(EXTRA_SOURCE_NAME) ?: ""
        sourceIndex = intent.getIntExtra(EXTRA_SOURCE_INDEX, 0)
        epNames = intent.getStringArrayListExtra(EXTRA_EP_NAMES) ?: arrayListOf()
        epUrls = intent.getStringArrayListExtra(EXTRA_EP_URLS) ?: arrayListOf()
        currentEp = intent.getIntExtra(EXTRA_EPISODE_INDEX, 0)
        val startPos = intent.getLongExtra(EXTRA_START_POSITION, 0L)
        title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        uiState.value = uiState.value.copy(currentEp = currentEp)

        initPlayer()
        if (isLive) {
            liveUrls = intent.getStringArrayListExtra(EXTRA_LIVE_URLS)
                ?: arrayListOf(intent.getStringExtra(EXTRA_LIVE_URL) ?: "")
            liveUrls = liveUrls.filter { it.isNotBlank() }
            liveUrlIndex = 0
            playUrl(liveUrls.firstOrNull() ?: "", 0L)
        } else {
            playEpisode(currentEp, startPos)
        }

        setContent { JyTheme { PlayerScreen() } }

        // 历史保存 + 直播看门狗
        lifecycleScope.launch {
            while (true) {
                delay(1000)
                val p = player ?: continue
                if (!isLive && p.duration > 0) {
                    saveHistory(p.currentPosition, p.duration)
                }
                if (isLive && bufferingSince > 0 &&
                    System.currentTimeMillis() - bufferingSince > 15000
                ) {
                    bufferingSince = 0L
                    if (liveUrlIndex + 1 < liveUrls.size) {
                        liveUrlIndex++
                        playDirect(liveUrls[liveUrlIndex], 0L, emptyMap())
                    } else {
                        p.stop()
                        uiState.value = uiState.value.copy(
                            buffering = false,
                            error = "该频道信号不佳或已失效\n请返回选择其他频道",
                        )
                    }
                }
            }
        }
    }

    private fun initPlayer() {
        val factory = OkHttpDataSource.Factory(Http.mediaClient).setUserAgent(Http.UA)
        dsFactory = factory
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(factory))
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        bufferingSince = if (state == Player.STATE_BUFFERING)
                            System.currentTimeMillis() else 0L
                        uiState.value = uiState.value.copy(
                            buffering = state == Player.STATE_BUFFERING,
                            error = if (state == Player.STATE_IDLE) uiState.value.error else null,
                        )
                        if (state == Player.STATE_ENDED && !isLive) nextEpisode()
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        if (isLive && liveUrlIndex + 1 < liveUrls.size) {
                            liveUrlIndex++
                            playDirect(liveUrls[liveUrlIndex], 0L, emptyMap())
                            return
                        }
                        val cur = if (isLive) liveUrls.getOrNull(liveUrlIndex) else epUrls.getOrNull(currentEp)
                        if (!isLive && cur != null && sniffTriedFor != cur) {
                            sniffTriedFor = cur
                            sniffAndPlay(cur, 0L)
                            return
                        }
                        uiState.value = uiState.value.copy(
                            buffering = false, sniffing = false,
                            error = "播放失败：${error.errorCodeName}\n该地址可能需要解析或已失效，试试其他线路/站点",
                        )
                    }
                })
            }
    }

    private fun playUrl(url: String, startPos: Long) {
        if (!isLive && !WebSniffer.looksLikeMedia(url)) {
            sniffAndPlay(url, startPos)
            return
        }
        playDirect(url, startPos, emptyMap())
    }

    private fun playDirect(url: String, startPos: Long, headers: Map<String, String>) {
        val p = player ?: return
        playHeaders = headers
        dsFactory?.setDefaultRequestProperties(headers)
        uiState.value = uiState.value.copy(error = null, buffering = true, sniffing = false)
        p.setMediaItem(MediaItem.Builder().setUri(url).build())
        p.prepare()
        if (startPos > 0) p.seekTo(startPos)
        p.playWhenReady = true
    }

    private fun sniffAndPlay(pageUrl: String, startPos: Long) {
        val candidates = mutableListOf(pageUrl)
        val cfg = (SourceManager.configState.value as? ConfigState.Success)?.config
        cfg?.parses?.filter { it.type == 0 && it.url.startsWith("http") }?.take(3)?.forEach {
            candidates.add(it.url + pageUrl)
        }
        sniffNext(pageUrl, candidates, 0, startPos)
    }

    private fun sniffNext(original: String, candidates: List<String>, index: Int, startPos: Long) {
        if (index >= candidates.size) {
            playDirect(original, startPos, emptyMap())
            return
        }
        uiState.value = uiState.value.copy(error = null, buffering = true, sniffing = true)
        val sn = sniffer ?: WebSniffer(this).also { sniffer = it }
        sn.sniff(candidates[index], object : WebSniffer.Callback {
            override fun onFound(url: String, headers: Map<String, String>) {
                runOnUiThread { playDirect(url, startPos, headers) }
            }
            override fun onFailed() {
                runOnUiThread { sniffNext(original, candidates, index + 1, startPos) }
            }
        })
    }

    private fun playEpisode(index: Int, startPos: Long = 0L) {
        val url = epUrls.getOrNull(index) ?: return
        sniffTriedFor = null
        currentEp = index
        uiState.value = uiState.value.copy(currentEp = index)
        episodeView?.text = epNames.getOrNull(index) ?: ""
        playUrl(url, startPos)
    }

    private fun nextEpisode() {
        if (currentEp + 1 < epUrls.size) playEpisode(currentEp + 1)
    }

    private fun prevEpisode() {
        if (currentEp > 0) playEpisode(currentEp - 1)
    }

    private var lastSave = 0L
    private fun saveHistory(position: Long, duration: Long) {
        if (isLive || vodId.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastSave < 5000) return
        lastSave = now
        val db = AppDatabase.get(this)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                db.history().upsert(
                    HistoryEntity(
                        siteKey = siteKey, vodId = vodId,
                        vodName = title, vodPic = pic,
                        sourceName = sourceName, sourceIndex = sourceIndex,
                        episodeIndex = currentEp, episodeName = epNames.getOrNull(currentEp) ?: "",
                        position = position, duration = duration,
                    )
                )
            }
        }
    }

    private fun hideSystemUi() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // ---------------- 控制器装配 ----------------

    private fun createPlayerView(): PlayerView {
        val view = LayoutInflater.from(this).inflate(R.layout.player_view, null) as PlayerView
        view.player = player
        view.controllerShowTimeoutMs = 4000

        titleView = view.findViewById(R.id.video_title)
        episodeView = view.findViewById(R.id.video_episode)
        speedView = view.findViewById(R.id.btn_speed)

        titleView?.text = title
        episodeView?.text = if (isLive) "直播" else epNames.getOrNull(currentEp) ?: ""

        view.findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener { finish() }
        view.findViewById<ImageButton>(R.id.btn_aspect)?.setOnClickListener {
            uiState.value = uiState.value.copy(scaleMode = (uiState.value.scaleMode + 1) % 3)
        }
        view.findViewById<ImageButton>(R.id.btn_prev_ep)?.setOnClickListener { prevEpisode() }
        view.findViewById<ImageButton>(R.id.btn_next_ep)?.setOnClickListener { nextEpisode() }
        speedView?.setOnClickListener {
            val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
            speed = speeds[(speeds.indexOf(speed) + 1) % speeds.size]
            player?.setPlaybackSpeed(speed)
            speedView?.text = "${speed}x"
        }
        view.findViewById<TextView>(R.id.btn_episodes)?.setOnClickListener {
            uiState.value = uiState.value.copy(episodesVisible = !uiState.value.episodesVisible)
            view.hideController()
        }

        // 直播模式隐藏进度相关控件
        if (isLive) {
            listOf(
                androidx.media3.ui.R.id.exo_position,
                androidx.media3.ui.R.id.exo_duration,
                androidx.media3.ui.R.id.exo_progress,
                androidx.media3.ui.R.id.exo_rew,
                androidx.media3.ui.R.id.exo_ffwd,
                R.id.btn_prev_ep,
                R.id.btn_next_ep,
                R.id.btn_episodes,
            ).forEach { view.findViewById<View>(it)?.visibility = View.GONE }
        }

        view.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                uiState.value = uiState.value.copy(controlsVisible = visibility == View.VISIBLE)
            }
        )
        return view
    }

    // ---------------- Compose 浮层 ----------------

    @Composable
    private fun PlayerScreen() {
        val st by uiState
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // 视频画面（官方控制器）
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { createPlayerView().also { playerView = it } },
                update = { view ->
                    view.resizeMode = when (st.scaleMode) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // 手势层：仅控制器隐藏时接管（点击唤出/双击快进/滑动亮度音量进度）
            if (!st.controlsVisible) {
                var seekAccum by remember { mutableStateOf(0f) }
                var seekBase by remember { mutableStateOf(0L) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(isLive) {
                            detectTapGestures(
                                onTap = { playerView?.showController() },
                                onDoubleTap = { offset ->
                                    if (!isLive) {
                                        val p = player ?: return@detectTapGestures
                                        if (offset.x < size.width / 2) {
                                            p.seekTo((p.currentPosition - 10000).coerceAtLeast(0))
                                            uiState.value = uiState.value.copy(gestureHint = "⏪ -10s")
                                        } else {
                                            p.seekTo(p.currentPosition + 10000)
                                            uiState.value = uiState.value.copy(gestureHint = "⏩ +10s")
                                        }
                                    }
                                },
                            )
                        }
                        .pointerInput(isLive) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    if (!isLive) {
                                        seekAccum = 0f
                                        seekBase = player?.currentPosition ?: 0L
                                    }
                                },
                                onDragEnd = {
                                    if (!isLive) {
                                        val target = (seekBase + (seekAccum * 120).toLong())
                                            .coerceIn(0L, player?.duration ?: Long.MAX_VALUE)
                                        player?.seekTo(target)
                                        uiState.value = uiState.value.copy(gestureHint = null)
                                    }
                                },
                            ) { change, dragAmount ->
                                if (isLive) return@detectHorizontalDragGestures
                                change.consume()
                                seekAccum += dragAmount
                                val deltaSec = (seekAccum * 120 / 1000).toInt()
                                val sign = if (deltaSec >= 0) "+" else "-"
                                uiState.value = uiState.value.copy(
                                    gestureHint = "$sign${kotlin.math.abs(deltaSec)}s"
                                )
                            }
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = { uiState.value = uiState.value.copy(gestureHint = null) },
                            ) { change, dragAmount ->
                                change.consume()
                                val isLeft = change.position.x < size.width / 2
                                if (isLeft) {
                                    val lp = window.attributes
                                    val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                    val next = (cur - dragAmount / size.height).coerceIn(0.02f, 1f)
                                    lp.screenBrightness = next
                                    window.attributes = lp
                                    uiState.value = uiState.value.copy(
                                        gestureHint = "亮度 ${(next * 100).toInt()}%"
                                    )
                                } else {
                                    val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                                    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                    val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                    val next = (cur - (dragAmount / (size.height / max.toFloat())).toInt())
                                        .coerceIn(0, max)
                                    if (next != cur) am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, next, 0)
                                    uiState.value = uiState.value.copy(
                                        gestureHint = if (next == 0) "静音" else "音量 $next/$max"
                                    )
                                }
                            }
                        },
                )
            }

            // 手势反馈（短暂显示后自动消失）
            st.gestureHint?.let { hint ->
                LaunchedEffect(hint) {
                    delay(900)
                    if (uiState.value.gestureHint == hint) {
                        uiState.value = uiState.value.copy(gestureHint = null)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xAA000000))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(hint, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 缓冲 / 嗅探
            if (st.buffering && st.error == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
                    Text(
                        if (st.sniffing) "正在嗅探真实播放地址…" else "加载中…",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            // 错误
            if (st.error != null) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        st.error!!, color = Color.White, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (!isLive) {
                        Button(
                            onClick = { nextEpisode() },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            modifier = Modifier.padding(top = 14.dp),
                        ) { Text("播放下一集") }
                    }
                }
            }

            // 选集抽屉
            if (st.episodesVisible && !isLive) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(230.dp)
                        .align(Alignment.CenterEnd)
                        .background(Color(0xF2141518))
                        .clickable(enabled = true, onClick = {}), // 拦截点击
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "选集 (${epNames.size})",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "关闭",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    uiState.value = uiState.value.copy(episodesVisible = false)
                                },
                            )
                        }
                        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            itemsIndexed(epNames) { i, name ->
                                val sel = i == st.currentEp
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) Accent.copy(alpha = 0.22f) else Color.Transparent)
                                        .clickable {
                                            playEpisode(i)
                                            uiState.value = uiState.value.copy(episodesVisible = false)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                ) {
                                    Text(
                                        name,
                                        color = if (sel) Accent else Color.White.copy(alpha = 0.85f),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        player?.let { saveHistory(it.currentPosition, it.duration) }
    }

    override fun onDestroy() {
        super.onDestroy()
        sniffer?.release()
        player?.let { saveHistory(it.currentPosition, it.duration) }
        playerView?.player = null
        player?.release()
        player = null
    }
}
