package com.juying.tv.ui.live

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.juying.tv.data.source.ConfigState
import com.juying.tv.player.PlayerActivity
import com.juying.tv.ui.LiveViewModel
import com.juying.tv.ui.MainViewModel
import com.juying.tv.ui.components.BrandBar
import com.juying.tv.ui.components.EmptyView
import com.juying.tv.ui.components.ErrorView
import com.juying.tv.ui.components.LoadingView
import com.juying.tv.ui.theme.*

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

        // 直播源切换
        if (state.liveSources.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.liveSources.forEachIndexed { i, src ->
                    val sel = i == state.sourceIndex
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) Primary else CardDark)
                            .clickable { vm.selectSource(i) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(src.name, color = if (sel) androidx.compose.ui.graphics.Color.White else TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        when {
            state.loading -> LoadingView("加载频道…")
            state.error != null -> ErrorView(state.error!!) {
                vm.bind((configState as? ConfigState.Success)?.config)
            }
            state.channels.isEmpty() -> EmptyView("暂无频道")
            else -> {
                Row(Modifier.fillMaxSize()) {
                    // 分组列表
                    LazyColumn(
                        modifier = Modifier.width(92.dp).fillMaxHeight().background(SurfaceDark),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.groups) { g ->
                            val sel = g == state.selectedGroup
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.selectGroup(g) }
                                    .background(if (sel) PrimarySoft else androidx.compose.ui.graphics.Color.Transparent)
                                    .padding(horizontal = 10.dp, vertical = 13.dp),
                            ) {
                                Text(
                                    g,
                                    color = if (sel) Primary else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    // 频道网格
                    val channels = state.channels.filter { it.group == state.selectedGroup }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(channels, key = { it.group + it.name }) { ch ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
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
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("📺", fontSize = 14.sp)
                                Text(
                                    " ${ch.name}",
                                    color = TextPrimary, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
