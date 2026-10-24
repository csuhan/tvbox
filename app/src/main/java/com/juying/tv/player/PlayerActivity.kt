package com.juying.tv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.juying.tv.data.local.AppDatabase
import com.juying.tv.data.local.HistoryEntity
import com.juying.tv.data.remote.Http
import com.juying.tv.data.source.ConfigState
import com.juying.tv.data.source.SourceManager
import com.juying.tv.ui.theme.JyTheme
import com.juying.tv.ui.theme.Primary
import com.juying.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // 播放状态（Compose 驱动）
    private val uiState = mutableStateOf(PlayerUiState())

    data class PlayerUiState(
        val title: String = "",
        val episodeName: String = "",
        val playing: Boolean = false,
        val buffering: Boolean = true,
        val sniffing: Boolean = false,
        val error: String? = null,
        val controlsVisible: Boolean = true,
        val episodesVisible: Boolean = false,
        val speed: Float = 1.0f,
        val scaleMode: Int = 0, // 0 fit 1 fill 2 zoom
        val position: Long = 0L,
        val duration: Long = 0L,
        val currentEp: Int = 0,
        val locked: Boolean = false,
        val gestureHint: String? = null,
    )

    // 点播数据
    private var epNames: List<String> = emptyList()
    private var epUrls: List<String> = emptyList()
    private var currentEp = 0
    private var isLive = false
    private var liveUrls: List<String> = emptyList()
    private var liveUrlIndex = 0
    private var bufferingSince = 0L
    private var siteKey = ""
    private var vodId = ""
    private var pic = ""
    private var sourceName = ""
    private var sourceIndex = 0

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
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        uiState.value = uiState.value.copy(
            title = title,
            episodeName = if (isLive) "直播" else epNames.getOrNull(currentEp) ?: "",
            currentEp = currentEp,
        )

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

        setContent {
            JyTheme { PlayerScreen() }
        }

        // 进度轮询 + 历史保存 + 直播缓冲看门狗
        lifecycleScope.launch {
            while (true) {
                delay(1000)
                val p = player ?: continue
                if (!isLive && p.duration > 0) {
                    uiState.value = uiState.value.copy(position = p.currentPosition, duration = p.duration)
                    saveHistory(p.currentPosition, p.duration)
                }
                // 直播缓冲超过 15 秒：换下一条线路，没有则报错
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
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        uiState.value = uiState.value.copy(playing = isPlaying)
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_BUFFERING) {
                            bufferingSince = System.currentTimeMillis()
                        } else {
                            bufferingSince = 0L
                        }
                        uiState.value = uiState.value.copy(
                            buffering = state == Player.STATE_BUFFERING,
                            error = if (state == Player.STATE_IDLE) uiState.value.error else null,
                        )
                        if (state == Player.STATE_ENDED && !isLive) nextEpisode()
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        // 直播：自动尝试下一条线路
                        if (isLive && liveUrlIndex + 1 < liveUrls.size) {
                            liveUrlIndex++
                            playDirect(liveUrls[liveUrlIndex], 0L, emptyMap())
                            return
                        }
                        val cur = if (isLive) liveUrls.getOrNull(liveUrlIndex) else epUrls.getOrNull(currentEp)
                        // 直连失败且未嗅探过 → 自动嗅探一次
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
                    override fun onVideoSizeChanged(videoSize: VideoSize) {}
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
        val item = MediaItem.Builder().setUri(url).build()
        p.setMediaItem(item)
        p.prepare()
        if (startPos > 0) p.seekTo(startPos)
        p.playWhenReady = true
    }

    private fun sniffAndPlay(pageUrl: String, startPos: Long) {
        // 候选：原页面 + 接口配置里的解析API(jx)
        val candidates = mutableListOf(pageUrl)
        val cfg = (SourceManager.configState.value as? ConfigState.Success)?.config
        cfg?.parses?.filter { it.type == 0 && it.url.startsWith("http") }?.take(3)?.forEach {
            candidates.add(it.url + pageUrl)
        }
        sniffNext(pageUrl, candidates, 0, startPos)
    }

    private fun sniffNext(original: String, candidates: List<String>, index: Int, startPos: Long) {
        if (index >= candidates.size) {
            // 全部失败，直接尝试原地址
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
        uiState.value = uiState.value.copy(
            episodeName = epNames.getOrNull(index) ?: "",
            currentEp = index,
            position = 0, duration = 0,
        )
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
                    vodName = uiState.value.title, vodPic = pic,
                    sourceName = sourceName, sourceIndex = sourceIndex,
                    episodeIndex = currentEp, episodeName = uiState.value.episodeName,
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

    private fun toggleControls() {
        uiState.value = uiState.value.copy(controlsVisible = !uiState.value.controlsVisible)
    }

    // ---------------- UI ----------------

    @Composable
    private fun PlayerScreen() {
        val st by uiState
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // 视频画面
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = this@PlayerActivity.player
                        playerView = this
                        resizeMode = when (st.scaleMode) {
                            1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        setOnClickListener { toggleControls() }
                    }
                },
                update = { view ->
                    view.resizeMode = when (st.scaleMode) {
                        1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // 手势层：左半屏亮度 / 右半屏音量 / 水平拖动快进退
            var seekAccum by remember { mutableStateOf(0f) }
            var seekBase by remember { mutableStateOf(0L) }
            Box(
                Modifier
                    .fillMaxSize()
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
                                gestureHint = "⏩ $sign${kotlin.math.abs(deltaSec)}s"
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
                                // 亮度
                                val lp = window.attributes
                                val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                val next = (cur - dragAmount / size.height).coerceIn(0.02f, 1f)
                                lp.screenBrightness = next
                                window.attributes = lp
                                uiState.value = uiState.value.copy(
                                    gestureHint = "☀️ ${(next * 100).toInt()}%"
                                )
                            } else {
                                // 音量
                                val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                                val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                val next = (cur - (dragAmount / (size.height / max.toFloat())).toInt())
                                    .coerceIn(0, max)
                                if (next != cur) am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, next, 0)
                                uiState.value = uiState.value.copy(
                                    gestureHint = if (next == 0) "🔇 静音" else "🔊 $next/$max"
                                )
                            }
                        }
                    },
            )

            // 手势反馈
            st.gestureHint?.let { hint ->
                Box(
                    Modifier.align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xAA000000))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(hint, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 缓冲
            if (st.buffering && st.error == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Primary)
                    Text(if (st.sniffing) "正在嗅探真实播放地址…" else "加载中…",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }

            // 错误
            if (st.error != null) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(st.error!!, color = Color.White, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (!isLive) {
                        Button(
                            onClick = { nextEpisode() },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            modifier = Modifier.padding(top = 12.dp),
                        ) { Text("播放下一集") }
                    }
                }
            }

            // 控制层
            if (st.controlsVisible) {
                // 顶栏
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xAA000000), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(st.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (st.episodeName.isNotBlank()) {
                            Text(st.episodeName, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                    // 倍速
                    TextButton(onClick = {
                        val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                        val next = speeds[(speeds.indexOf(st.speed) + 1) % speeds.size]
                        player?.setPlaybackSpeed(next)
                        uiState.value = uiState.value.copy(speed = next)
                    }) {
                        Text("${st.speed}x", color = Color.White, fontSize = 14.sp)
                    }
                    // 画面比例
                    IconButton(onClick = {
                        uiState.value = uiState.value.copy(scaleMode = (st.scaleMode + 1) % 3)
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "比例", tint = Color.White)
                    }
                }

                // 底部控制
                Column(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000))))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (!isLive) {
                        // 进度条
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(st.position), color = Color.White, fontSize = 12.sp)
                            Slider(
                                value = if (st.duration > 0) st.position.toFloat() / st.duration else 0f,
                                onValueChange = { v ->
                                    val target = (v * st.duration).toLong()
                                    player?.seekTo(target)
                                    uiState.value = uiState.value.copy(position = target)
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Primary, activeTrackColor = Primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                ),
                            )
                            Text(formatTime(st.duration), color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        if (!isLive) {
                            IconButton(onClick = { prevEpisode() }, enabled = st.currentEp > 0) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "上一集",
                                    tint = if (st.currentEp > 0) Color.White else Color.White.copy(alpha = 0.3f))
                            }
                        }
                        IconButton(onClick = {
                            val p = player ?: return@IconButton
                            if (p.isPlaying) p.pause() else p.play()
                        }, modifier = Modifier.size(56.dp)) {
                            Icon(
                                if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "播放/暂停", tint = Color.White,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        if (!isLive) {
                            IconButton(onClick = { nextEpisode() }, enabled = st.currentEp < epUrls.size - 1) {
                                Icon(Icons.Default.SkipNext, contentDescription = "下一集",
                                    tint = if (st.currentEp < epUrls.size - 1) Color.White else Color.White.copy(alpha = 0.3f))
                            }
                            TextButton(onClick = {
                                uiState.value = uiState.value.copy(episodesVisible = !st.episodesVisible)
                            }) {
                                Text("选集", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // 选集抽屉
            if (st.episodesVisible && !isLive) {
                Box(
                    Modifier.fillMaxHeight().width(220.dp).align(Alignment.CenterEnd)
                        .background(Color(0xEE10131C)),
                ) {
                    Column {
                        Text("选集 (${epNames.size})", color = Color.White, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            itemsIndexed(epNames) { i, name ->
                                val sel = i == st.currentEp
                                Box(
                                    Modifier.fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) Primary.copy(alpha = 0.25f) else Color.Transparent)
                                        .clickable {
                                            playEpisode(i)
                                            uiState.value = uiState.value.copy(episodesVisible = false)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(name, color = if (sel) Primary else Color.White.copy(alpha = 0.85f),
                                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
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
