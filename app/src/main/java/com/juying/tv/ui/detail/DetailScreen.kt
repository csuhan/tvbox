package com.juying.tv.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.juying.tv.data.model.Episode
import com.juying.tv.player.PlayerActivity
import com.juying.tv.player.PlayerHolder
import com.juying.tv.player.WebSniffer
import com.juying.tv.ui.DetailViewModel
import com.juying.tv.ui.MainViewModel
import com.juying.tv.ui.components.ErrorView
import com.juying.tv.ui.components.LoadingView
import com.juying.tv.ui.components.SectionHeader
import com.juying.tv.ui.theme.AppColors

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 芒果风详情页：顶部内嵌播放器(进页即播) + 标签 + 操作行 + 数字选集 */
@Composable
fun DetailScreen(nav: NavHostController, vodId: String, mainVm: MainViewModel = viewModel()) {
    val vm: DetailViewModel = viewModel()
    val site by mainVm.currentSite.collectAsState()
    val state by vm.state.collectAsState()
    val isFav by vm.isFavorite.collectAsState()
    val context = LocalContext.current
    // 交接标识：站点 key 会在后台刷新后被重写（mergeConfigs 加命名空间），
    // 因此小窗/大窗交接改用与站点 key 无关的页面级稳定 ID
    val handoffId = remember(vodId) { "$vodId@${java.util.UUID.randomUUID()}" }

    LaunchedEffect(site?.key, vodId) { vm.load(site, vodId) }
    LifecycleResumeEffect(Unit) {
        vm.refreshHistory()
        onPauseOrDispose { }
    }

    fun playFullscreen(sourceIndex: Int, episodeIndex: Int, startPos: Long = 0L) {
        val d = state.detail ?: return
        val src = d.sources.getOrNull(sourceIndex) ?: return
        // 小窗正在播放本页影片：标记交接，大窗复用同一播放器实例
        if (PlayerHolder.player != null && PlayerHolder.vodKey == handoffId) {
            PlayerHolder.handoff = true
        }
        context.startActivity(
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_SITE_KEY, site?.key ?: "")
                putExtra(PlayerActivity.EXTRA_HANDOFF_ID, handoffId)
                putExtra(PlayerActivity.EXTRA_VOD_ID, d.vodId)
                putExtra(PlayerActivity.EXTRA_TITLE, d.vodName)
                putExtra(PlayerActivity.EXTRA_PIC, d.vodPic)
                putExtra(PlayerActivity.EXTRA_SOURCE_NAME, src.name)
                putExtra(PlayerActivity.EXTRA_SOURCE_INDEX, sourceIndex)
                putExtra(PlayerActivity.EXTRA_EPISODE_INDEX, episodeIndex)
                putExtra(PlayerActivity.EXTRA_START_POSITION, startPos)
                putStringArrayListExtra(PlayerActivity.EXTRA_EP_NAMES, ArrayList(src.episodes.map { it.name }))
                putStringArrayListExtra(PlayerActivity.EXTRA_EP_URLS, ArrayList(src.episodes.map { it.url }))
            }
        )
    }

    Column(Modifier.fillMaxSize().background(AppColors.card)) {
        when {
            state.loading -> {
                DetailTopBar(nav)
                LoadingView()
            }
            state.error != null -> {
                DetailTopBar(nav)
                ErrorView(state.error!!) { vm.load(site, vodId) }
            }
            state.detail != null -> {
                val d = state.detail!!
                // 内嵌播放器状态
                var playingIndex by remember { mutableStateOf(-1) } // -1 = 未在内嵌播放
                var embeddedPosition by remember { mutableStateOf(0L) }
                val resume = state.history

                Column(Modifier.fillMaxSize()) {
                    // ===== 内嵌播放区（16:9）=====
                    EmbeddedPlayer(
                        poster = d.vodPic,
                        vodKey = handoffId,
                        episodes = d.sources.getOrNull(state.sourceIndex)?.episodes ?: emptyList(),
                        autoPlayIndex = if (resume != null && playingIndex < 0) resume.episodeIndex else 0,
                        startPosition = resume?.position ?: 0L,
                        playingIndex = playingIndex,
                        onPlayingIndexChange = { playingIndex = it },
                        onPositionChange = { embeddedPosition = it },
                        onFullscreen = {
                            val idx = if (playingIndex >= 0) playingIndex
                            else resume?.episodeIndex ?: 0
                            val pos = PlayerHolder.player?.currentPosition ?: embeddedPosition
                            playFullscreen(state.sourceIndex, idx, pos)
                        },
                        onBack = { nav.popBackStack() },
                    )

                    // ===== 信息区（可滚动）=====
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // 标题 + 标签
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                d.vodName,
                                color = AppColors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val tags = listOfNotNull(
                                d.typeName.ifBlank { null },
                                d.vodYear.ifBlank { null },
                                d.vodArea.ifBlank { null },
                                d.vodRemarks.ifBlank { null },
                            )
                            if (tags.isNotEmpty()) {
                                Row(
                                    Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    tags.forEach { tag ->
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AppColors.fill)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(tag, color = AppColors.textSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 操作行：分享 | 收藏 | 下载(占位→选集提示)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            ActionItem("分享", Icons.Outlined.Share) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "《${d.vodName}》${d.vodContent.take(80)}")
                                }
                                context.startActivity(Intent.createChooser(send, "分享"))
                            }
                            ActionItem(
                                if (isFav) "已收藏" else "收藏",
                                if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                tint = if (isFav) AppColors.accent else AppColors.textPrimary,
                            ) { vm.toggleFavorite() }
                            ActionItem("简介", Icons.AutoMirrored.Outlined.Sort) { }
                        }
                        HorizontalDivider(color = AppColors.divider, thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 8.dp))

                        // 线路
                        if (d.sources.size > 1) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                d.sources.forEachIndexed { i, src ->
                                    val sel = i == state.sourceIndex
                                    Text(
                                        src.name,
                                        color = if (sel) AppColors.accent else AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier
                                            .clickable { vm.selectSource(i); playingIndex = -1 }
                                            .padding(vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        // 选集（数字格 + 正在播放标记）
                        val src = d.sources.getOrNull(state.sourceIndex)
                        if (src != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "选集",
                                    color = AppColors.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "共 ${src.episodes.size} 集",
                                    color = AppColors.textSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            EpisodeNumberGrid(
                                episodes = src.episodes,
                                playingIndex = playingIndex,
                                resumeName = resume?.episodeName,
                                onClick = { ep ->
                                    val idx = src.episodes.indexOf(ep)
                                    if (WebSniffer.looksLikeMedia(ep.url)) {
                                        playingIndex = idx
                                    } else {
                                        playFullscreen(state.sourceIndex, idx)
                                    }
                                },
                            )
                        }

                        // 简介
                        if (d.vodContent.isNotBlank()) {
                            SectionHeader("简介")
                            var expanded by remember { mutableStateOf(false) }
                            Text(
                                d.vodContent,
                                color = AppColors.textPrimary.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .clickable { expanded = !expanded },
                            )
                            if (d.vodDirector.isNotBlank() || d.vodActor.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                if (d.vodDirector.isNotBlank()) {
                                    Text(
                                        "导演：${d.vodDirector}",
                                        color = AppColors.textSecondary, fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                    )
                                }
                                if (d.vodActor.isNotBlank()) {
                                    Text(
                                        "主演：${d.vodActor}",
                                        color = AppColors.textSecondary, fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp).navigationBarsPadding())
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(nav: NavHostController) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = AppColors.textPrimary)
        }
    }
}

@Composable
private fun ActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = AppColors.textPrimary,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = AppColors.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/** 内嵌播放器：海报待机 → 点击/自动播放直连剧集 → 右下全屏 */
@Composable
private fun EmbeddedPlayer(
    poster: String,
    vodKey: String,
    episodes: List<Episode>,
    autoPlayIndex: Int,
    startPosition: Long,
    playingIndex: Int,
    onPlayingIndexChange: (Int) -> Unit,
    onPositionChange: (Long) -> Unit,
    onFullscreen: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var buffering by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    // 需要播放的剧集
    val targetIndex = if (playingIndex >= 0) playingIndex else -1

    // 自动播放：仅当历史续播或首集为直连媒体时
    LaunchedEffect(episodes) {
        if (playingIndex < 0 && episodes.isNotEmpty()) {
            val idx = autoPlayIndex.coerceIn(0, episodes.size - 1)
            if (WebSniffer.looksLikeMedia(episodes[idx].url)) {
                onPlayingIndexChange(idx)
            }
        }
    }

    // 播放切换（复用全局共享播放器，大窗返回时同集不重新加载）
    LaunchedEffect(targetIndex) {
        if (targetIndex < 0) return@LaunchedEffect
        val ep = episodes.getOrNull(targetIndex) ?: return@LaunchedEffect
        val tag = PlayerHolder.tagOf(vodKey, targetIndex)
        val p = PlayerHolder.obtain(context, vodKey)
        player = p
        if (PlayerHolder.currentTag == tag) {
            // 同一集已在大窗/小窗 prepare 过：直接续播
            buffering = p.playbackState == Player.STATE_BUFFERING
            p.playWhenReady = true
            return@LaunchedEffect
        }
        buffering = true
        p.setMediaItem(MediaItem.fromUri(ep.url))
        p.prepare()
        if (startPosition > 0 && targetIndex == autoPlayIndex) p.seekTo(startPosition)
        p.playWhenReady = true
        PlayerHolder.currentTag = tag
    }

    // 从大窗返回：若在大窗里切换了集数，同步过来（不会触发重新加载）
    val latestEpisodes by rememberUpdatedState(episodes)
    val latestOnIndexChange by rememberUpdatedState(onPlayingIndexChange)
    val latestPlayingIndex by rememberUpdatedState(playingIndex)
    LifecycleResumeEffect(vodKey) {
        val tag = PlayerHolder.currentTag
        val cur = latestPlayingIndex
        if (tag != null && PlayerHolder.vodKey == vodKey && cur >= 0) {
            val idx = tag.substringAfterLast('#').toIntOrNull()
            if (idx != null && idx != cur && idx in latestEpisodes.indices) {
                latestOnIndexChange(idx)
            }
        }
        // 大窗接管过视频输出 surface，返回后强制小窗重新附着，否则有声音但画面全黑
        playerViewRef?.let { v ->
            player?.let { p ->
                v.player = null
                v.player = p
            }
        }
        onPauseOrDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.let { onPositionChange(it.currentPosition) }
            // 小窗 → 大窗交接中不释放；否则离开详情页释放共享播放器
            if (!PlayerHolder.handoff) PlayerHolder.release()
            context.findActivity()?.window?.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            player = null
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
    ) {
        if (targetIndex >= 0 && player != null) {
            // 进度轮询（驱动控制条）
            LaunchedEffect(player) {
                while (true) {
                    kotlinx.coroutines.delay(500)
                    player?.let {
                        isPlaying = it.isPlaying
                        positionMs = it.currentPosition
                        durationMs = if (it.duration > 0) it.duration else 0L
                    }
                }
            }
            // 控制条自动隐藏
            LaunchedEffect(controlsVisible) {
                if (controlsVisible) {
                    kotlinx.coroutines.delay(3000)
                    controlsVisible = false
                }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                    }.also { playerViewRef = it }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            // 播放状态监听（共享播放器上必须可移除，避免泄漏/叠加；
            // 共享实例跨集存活，闭包必须用最新值，否则自动连播会跳到过期集数）
            val latestTarget by rememberUpdatedState(targetIndex)
            DisposableEffect(player) {
                val p = player
                if (p == null) {
                    onDispose { }
                } else {
                    val listener = object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            // 小窗播放中屏幕常亮，暂停/结束恢复自动息屏
                            val w = context.findActivity()?.window ?: return
                            if (isPlaying) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            buffering = state == Player.STATE_BUFFERING
                            if (state == Player.STATE_ENDED) {
                                if (PlayerHolder.fullscreenActive) return // 大窗接管中，不干预
                                // 自动下一集
                                val next = latestTarget + 1
                                if (next < latestEpisodes.size && WebSniffer.looksLikeMedia(latestEpisodes[next].url)) {
                                    latestOnIndexChange(next)
                                }
                            }
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            buffering = false
                            if (!PlayerHolder.fullscreenActive) {
                                latestOnIndexChange(-1) // 回退到海报，用户可进全屏(带嗅探)
                            }
                        }
                    }
                    p.addListener(listener)
                    onDispose { p.removeListener(listener) }
                }
            }
            // 点击显示/隐藏控制条
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { controlsVisible = !controlsVisible },
            )
            // 底部控制条：播放/暂停 + 进度 + 时间 + 全屏
            if (controlsVisible) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xB3000000))
                            )
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        player?.let { if (it.isPlaying) it.pause() else it.play() }
                    }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (isPlaying) Icons.Outlined.PauseCircleOutline
                            else Icons.Outlined.PlayCircleOutline,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Text(
                        formatMs(positionMs),
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                    Slider(
                        value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                        onValueChange = { v ->
                            val target = (v * durationMs).toLong()
                            player?.seekTo(target)
                            positionMs = target
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = AppColors.accent,
                            activeTrackColor = AppColors.accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                        ),
                    )
                    Text(
                        formatMs(durationMs),
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                    IconButton(onClick = onFullscreen, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.Fullscreen,
                            contentDescription = "全屏",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            if (buffering) {
                CircularProgressIndicator(
                    color = AppColors.accent,
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                    strokeWidth = 3.dp,
                )
            }
        } else {
            // 海报待机
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.55f,
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xB3000000))
                    .clickable {
                        val idx = autoPlayIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
                        if (episodes.isNotEmpty() && WebSniffer.looksLikeMedia(episodes[idx].url)) {
                            onPlayingIndexChange(idx)
                        } else {
                            onFullscreen()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        // 顶部渐变 + 返回
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(Brush.verticalGradient(listOf(Color(0x80000000), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
            }
        }
        // 非播放状态：右下全屏入口
        if (targetIndex < 0) {
            IconButton(
                onClick = onFullscreen,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            ) {
                Icon(Icons.Outlined.Fullscreen, "全屏", tint = Color.White)
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** 芒果风数字选集格：6 列，正在播放橙色标记 */
@Composable
private fun EpisodeNumberGrid(
    episodes: List<Episode>,
    playingIndex: Int,
    resumeName: String?,
    onClick: (Episode) -> Unit,
) {
    val columns = 6
    val rows = episodes.chunked(columns)
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { ep ->
                    val idx = episodes.indexOf(ep)
                    val isPlaying = idx == playingIndex
                    val isResume = playingIndex < 0 && ep.name == resumeName
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isPlaying -> AppColors.accent
                                    isResume -> AppColors.accentSoft
                                    else -> AppColors.fill
                                }
                            )
                            .clickable { onClick(ep) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ep.name.replace("第", "").replace("集", ""),
                            color = when {
                                isPlaying -> Color.White
                                isResume -> AppColors.accent
                                else -> AppColors.textPrimary.copy(alpha = 0.8f)
                            },
                            fontSize = 12.sp,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
