package com.juying.tv.ui.live

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.juying.tv.data.source.ConfigState
import com.juying.tv.player.PlayerActivity
import com.juying.tv.ui.LiveViewModel
import com.juying.tv.ui.MainViewModel
import com.juying.tv.ui.components.BrandBar
import com.juying.tv.ui.components.EmptyView
import com.juying.tv.ui.components.ErrorView
import com.juying.tv.ui.components.LoadingView
import com.juying.tv.ui.theme.*

/** 手机触屏版直播页：顶部横滑分组 + 全宽频道列表 */
@Composable
fun LiveScreen(nav: NavHostController, mainVm: MainViewModel) {
    val vm: LiveViewModel = viewModel()
    val configState by mainVm.configState.collectAsState()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(configState) {
        vm.bind((configState as? ConfigState.Success)?.config)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BrandBar(title = "直播", subtitle = state.liveSources.getOrNull(state.sourceIndex)?.name)

        when {
            state.loading -> LoadingView("加载频道…")
            state.error != null -> ErrorView(state.error!!) {
                vm.bind((configState as? ConfigState.Success)?.config)
            }
            state.channels.isEmpty() -> EmptyView("暂无频道")
            else -> {
                // 直播源选择（横滑 chips）
                if (state.liveSources.size > 1) {
                    ChipRow(
                        items = state.liveSources.map { it.name },
                        selectedIndex = state.sourceIndex,
                    ) { vm.selectSource(it) }
                }
                // 分组选择（横滑 chips）
                if (state.groups.size > 1) {
                    ChipRow(
                        items = state.groups,
                        selectedIndex = state.groups.indexOf(state.selectedGroup).coerceAtLeast(0),
                    ) { vm.selectGroup(state.groups[it]) }
                }

                // 频道列表（全宽大行，触屏友好）
                val channels = state.channels.filter { it.group == state.selectedGroup }
                Text(
                    "${state.selectedGroup ?: ""} · ${channels.size} 个频道",
                    color = TextSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(channels, key = { it.group + it.name }) { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardDark)
                                .clickable {
                                    context.startActivity(
                                        Intent(context, PlayerActivity::class.java).apply {
                                            putExtra(PlayerActivity.EXTRA_LIVE, true)
                                            putExtra(PlayerActivity.EXTRA_TITLE, ch.name)
                                            putExtra(PlayerActivity.EXTRA_LIVE_URL, ch.urls.first())
                                        }
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 台标 / 默认图标
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceDark),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!ch.logo.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ch.logo,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text("📺", fontSize = 17.sp)
                                }
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(
                                    ch.name,
                                    color = TextPrimary, fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFE53935))
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                    ) {
                                        Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (ch.urls.size > 1) {
                                        Text("  ${ch.urls.size} 条线路", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null, tint = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { i, name ->
            val sel = i == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (sel) Primary else CardDark)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    name,
                    color = if (sel) Color.White else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}
