package com.juying.tv.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.juying.tv.player.PlayerActivity
import com.juying.tv.ui.DetailViewModel
import com.juying.tv.ui.MainViewModel
import com.juying.tv.ui.components.ErrorView
import com.juying.tv.ui.components.LoadingView
import com.juying.tv.ui.components.SectionHeader
import com.juying.tv.ui.theme.AppColors

@Composable
fun DetailScreen(nav: NavHostController, vodId: String, mainVm: MainViewModel = viewModel()) {
    val vm: DetailViewModel = viewModel()
    val site by mainVm.currentSite.collectAsState()
    val state by vm.state.collectAsState()
    val isFav by vm.isFavorite.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(site?.key, vodId) { vm.load(site, vodId) }
    LifecycleResumeEffect(Unit) {
        vm.refreshHistory()
        onPauseOrDispose { }
    }

    fun play(sourceIndex: Int, episodeIndex: Int, startPos: Long = 0L) {
        val d = state.detail ?: return
        val src = d.sources.getOrNull(sourceIndex) ?: return
        context.startActivity(
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_SITE_KEY, site?.key ?: "")
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
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = AppColors.textPrimary)
            }
        }
        HorizontalDivider(color = AppColors.divider, thickness = 0.5.dp)

        when {
            state.loading -> LoadingView()
            state.error != null -> ErrorView(state.error!!) { vm.load(site, vodId) }
            state.detail != null -> {
                val d = state.detail!!
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    // 信息区：小海报 + 文字
                    Row(Modifier.padding(16.dp)) {
                        AsyncImage(
                            model = d.vodPic,
                            contentDescription = d.vodName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(104.dp)
                                .height(148.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppColors.fill),
                        )
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(
                                d.vodName,
                                color = AppColors.textPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 26.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = listOfNotNull(
                                d.typeName.ifBlank { null },
                                d.vodYear.ifBlank { null },
                                d.vodArea.ifBlank { null },
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(meta, color = AppColors.textSecondary, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 8.dp))
                            }
                            if (d.vodRemarks.isNotBlank()) {
                                Box(
                                    Modifier
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(AppColors.accentSoft)
                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                ) {
                                    Text(d.vodRemarks, color = AppColors.accent, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // 操作按钮
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val resume = state.history
                        val noPlay = d.sources.isEmpty()
                        Button(
                            enabled = !noPlay,
                            onClick = {
                                val h = state.history
                                if (h != null) play(h.sourceIndex, h.episodeIndex, h.position)
                                else play(state.sourceIndex, 0)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                if (resume != null) "  续播 ${resume.episodeName}" else "  立即播放",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                        OutlinedButton(
                            onClick = { vm.toggleFavorite() },
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isFav) AppColors.accent else AppColors.textSecondary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isFav) AppColors.accent else AppColors.divider,
                            ),
                            modifier = Modifier.height(44.dp),
                        ) {
                            Icon(
                                if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "收藏",
                                modifier = Modifier.size(19.dp),
                            )
                            Text(if (isFav) " 已收藏" else " 收藏", fontSize = 13.sp)
                        }
                    }

                    // 信息
                    Column(Modifier.padding(16.dp)) {
                        if (d.vodDirector.isNotBlank()) InfoLine("导演", d.vodDirector)
                        if (d.vodActor.isNotBlank()) InfoLine("主演", d.vodActor)
                        if (d.vodContent.isNotBlank()) {
                            InfoLine("简介", "")
                            var expanded by remember { mutableStateOf(false) }
                            Text(
                                d.vodContent,
                                color = AppColors.textPrimary.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { expanded = !expanded },
                            )
                        }
                    }

                    if (d.sources.isEmpty()) {
                        Box(
                            Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.accentSoft)
                                .padding(12.dp),
                        ) {
                            Text(
                                "该源未提供播放地址（vod_play_url 为空），可能是源服务端做了地址脱敏，或需要其他取址方式",
                                color = AppColors.accent,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    HorizontalDivider(color = AppColors.divider, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp))

                    // 线路（文字 Tab 风）
                    if (d.sources.size > 1) {
                        SectionHeader("播放线路")
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            d.sources.forEachIndexed { i, src ->
                                val sel = i == state.sourceIndex
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { vm.selectSource(i) },
                                ) {
                                    Text(
                                        "${src.name} (${src.episodes.size})",
                                        color = if (sel) AppColors.accent else AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Box(
                                        Modifier
                                            .padding(top = 4.dp)
                                            .width(16.dp)
                                            .height(2.5.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (sel) AppColors.accent else androidx.compose.ui.graphics.Color.Transparent)
                                    )
                                }
                            }
                        }
                    }

                    // 选集
                    val src = d.sources.getOrNull(state.sourceIndex)
                    if (src != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(AppColors.accent)
                            )
                            Text(
                                "选集",
                                color = AppColors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { vm.toggleReverse() },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Sort,
                                    contentDescription = null,
                                    tint = AppColors.textSecondary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(" 倒序", color = AppColors.textSecondary, fontSize = 12.sp)
                            }
                        }
                        val eps = if (state.reversed) src.episodes.asReversed() else src.episodes
                        EpisodeGrid(
                            episodes = eps,
                            highlight = state.history?.episodeName,
                            onClick = { ep ->
                                val idx = src.episodes.indexOf(ep)
                                play(state.sourceIndex, idx)
                            },
                        )
                    }
                    Spacer(Modifier.height(28.dp).navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(bottom = 6.dp)) {
        Text(label, color = AppColors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(40.dp))
        if (value.isNotBlank()) {
            Text(value, color = AppColors.textPrimary.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun EpisodeGrid(
    episodes: List<com.juying.tv.data.model.Episode>,
    highlight: String?,
    onClick: (com.juying.tv.data.model.Episode) -> Unit,
) {
    val columns = 4
    val rows = episodes.chunked(columns)
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { ep ->
                    val isHl = ep.name == highlight
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isHl) AppColors.accentSoft else AppColors.fill)
                            .clickable { onClick(ep) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ep.name,
                            color = if (isHl) AppColors.accent else AppColors.textPrimary.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
