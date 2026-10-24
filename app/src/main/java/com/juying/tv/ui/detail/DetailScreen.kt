package com.juying.tv.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.juying.tv.player.PlayerActivity
import com.juying.tv.ui.DetailViewModel
import com.juying.tv.ui.MainViewModel
import com.juying.tv.ui.components.ErrorView
import com.juying.tv.ui.components.LoadingView
import com.juying.tv.ui.theme.*

@Composable
fun DetailScreen(nav: NavHostController, vodId: String, mainVm: MainViewModel = viewModel()) {
    val vm: DetailViewModel = viewModel()
    val site by mainVm.currentSite.collectAsState()
    val state by vm.state.collectAsState()
    val isFav by vm.isFavorite.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(site?.key, vodId) { vm.load(site, vodId) }

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
                putStringArrayListExtra(
                    PlayerActivity.EXTRA_EP_NAMES,
                    ArrayList(src.episodes.map { it.name }),
                )
                putStringArrayListExtra(
                    PlayerActivity.EXTRA_EP_URLS,
                    ArrayList(src.episodes.map { it.url }),
                )
            }
        )
    }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        when {
            state.loading -> LoadingView()
            state.error != null -> ErrorView(state.error!!) { vm.load(site, vodId) }
            state.detail != null -> {
                val d = state.detail!!
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    // 头部：海报背景 + 信息
                    Box(Modifier.fillMaxWidth().height(300.dp)) {
                        AsyncImage(
                            model = d.vodPic, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            alpha = 0.35f,
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color(0x330C0F16), Color(0xCC0C0F16), BgDark)
                                )
                            )
                        )
                        Row(
                            Modifier.align(Alignment.BottomStart).padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            AsyncImage(
                                model = d.vodPic, contentDescription = d.vodName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(110.dp).height(156.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CardDark),
                            )
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(d.vodName, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val meta = listOfNotNull(
                                    d.typeName.ifBlank { null }, d.vodYear.ifBlank { null },
                                    d.vodArea.ifBlank { null },
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Text(meta, color = Secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                                }
                                if (d.vodRemarks.isNotBlank()) {
                                    Text(d.vodRemarks, color = Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.statusBarsPadding()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                        }
                    }

                    // 操作按钮
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val resume = state.history
                        Button(
                            onClick = {
                                val h = state.history
                                if (h != null) play(h.sourceIndex, h.episodeIndex, h.position)
                                else play(state.sourceIndex, 0)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(
                                if (resume != null) "  续播 ${resume.episodeName}" else "  立即播放",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                        OutlinedButton(
                            onClick = { vm.toggleFavorite() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isFav) Color(0xFFFF5C7A) else TextSecondary
                            ),
                            modifier = Modifier.height(46.dp),
                        ) {
                            Icon(
                                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "收藏",
                            )
                        }
                    }

                    // 信息区
                    Column(Modifier.padding(16.dp)) {
                        if (d.vodDirector.isNotBlank()) InfoLine("导演", d.vodDirector)
                        if (d.vodActor.isNotBlank()) InfoLine("主演", d.vodActor)
                        if (d.vodContent.isNotBlank()) {
                            var expanded by remember { mutableStateOf(false) }
                            Text("简介", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                d.vodContent, color = TextPrimary.copy(alpha = 0.85f), fontSize = 13.sp,
                                lineHeight = 20.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp).clickable { expanded = !expanded },
                            )
                        }
                    }

                    // 线路
                    if (d.sources.size > 1) {
                        Text("播放线路", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            d.sources.forEachIndexed { i, src ->
                                val sel = i == state.sourceIndex
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (sel) Primary else CardDark)
                                        .clickable { vm.selectSource(i) }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                ) {
                                    Text("${src.name} (${src.episodes.size})",
                                        color = if (sel) Color.White else TextSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // 选集
                    val src = d.sources.getOrNull(state.sourceIndex)
                    if (src != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("选集", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.toggleReverse() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Secondary, modifier = Modifier.size(14.dp))
                                Text(" 倒序", color = Secondary, fontSize = 13.sp)
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
                    Spacer(Modifier.height(32.dp).navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(bottom = 6.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(40.dp))
        Text(value, color = TextPrimary.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun EpisodeGrid(
    episodes: List<com.juying.tv.data.model.Episode>,
    highlight: String?,
    onClick: (com.juying.tv.data.model.Episode) -> Unit,
) {
    // 非懒加载网格（外层已可滚动）
    val columns = 4
    val rows = episodes.chunked(columns)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { ep ->
                    val isHl = ep.name == highlight
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isHl) PrimarySoft else CardDark)
                            .clickable { onClick(ep) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ep.name,
                            color = if (isHl) Primary else TextPrimary.copy(alpha = 0.85f),
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
