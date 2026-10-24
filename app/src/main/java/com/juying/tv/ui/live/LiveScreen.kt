package com.juying.tv.ui.live

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import com.juying.tv.data.source.ConfigState
import com.juying.tv.player.PlayerActivity
import com.juying.tv.ui.LiveViewModel
import com.juying.tv.ui.MainViewModel
import com.juying.tv.ui.components.EmptyView
import com.juying.tv.ui.components.ErrorView
import com.juying.tv.ui.components.LoadingView
import com.juying.tv.ui.theme.AppColors

/** 手机触屏版直播页：官方 TabRow 分组 + ListItem 频道列表 */
@Composable
fun LiveScreen(nav: NavHostController, mainVm: MainViewModel) {
    val vm: LiveViewModel = viewModel()
    val configState by mainVm.configState.collectAsState()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(configState) {
        vm.bind((configState as? ConfigState.Success)?.config)
    }

    Column(Modifier.fillMaxSize().background(AppColors.card)) {
        Spacer(Modifier.statusBarsPadding())

        when {
            state.loading -> LoadingView("加载频道…")
            state.error != null -> ErrorView(state.error!!) {
                vm.bind((configState as? ConfigState.Success)?.config)
            }
            state.channels.isEmpty() -> EmptyView("暂无频道")
            else -> {
                // 直播源 Tab
                if (state.liveSources.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = state.sourceIndex,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        edgePadding = 12.dp,
                        divider = {},
                        indicator = { positions ->
                            if (state.sourceIndex < positions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(positions[state.sourceIndex]),
                                    color = AppColors.accent,
                                )
                            }
                        },
                    ) {
                        state.liveSources.forEachIndexed { i, src ->
                            Tab(selected = i == state.sourceIndex, onClick = { vm.selectSource(i) }) {
                                Text(
                                    src.name,
                                    color = if (i == state.sourceIndex) AppColors.accent else AppColors.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (i == state.sourceIndex) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
                // 分组 Tab
                if (state.groups.size > 1) {
                    val gIndex = state.groups.indexOf(state.selectedGroup).coerceAtLeast(0)
                    ScrollableTabRow(
                        selectedTabIndex = gIndex,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        edgePadding = 12.dp,
                        divider = {},
                        indicator = { positions ->
                            if (gIndex < positions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(positions[gIndex]),
                                    color = AppColors.accent,
                                )
                            }
                        },
                    ) {
                        state.groups.forEachIndexed { i, g ->
                            Tab(selected = g == state.selectedGroup, onClick = { vm.selectGroup(g) }) {
                                Text(
                                    g,
                                    color = if (g == state.selectedGroup) AppColors.accent else AppColors.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (g == state.selectedGroup) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = AppColors.divider, thickness = 0.5.dp)

                // 频道列表（material3 ListItem）
                val channels = state.channels.filter { it.group == state.selectedGroup }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(
                        count = channels.size,
                        key = { i -> "${channels[i].group}_${channels[i].name}_$i" },
                    ) { i ->
                        val ch = channels[i]
                        ListItem(
                            headlineContent = {
                                Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(AppColors.accent)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            "LIVE",
                                            color = androidx.compose.ui.graphics.Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    if (ch.urls.size > 1) {
                                        Text(
                                            "  ${ch.urls.size} 条线路",
                                            color = AppColors.textSecondary,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.fill),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!ch.logo.isNullOrBlank()) {
                                        SubcomposeAsyncImage(
                                            model = ch.logo,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(
                                            Icons.Outlined.LiveTv,
                                            contentDescription = null,
                                            tint = AppColors.textSecondary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = AppColors.textSecondary,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = AppColors.card),
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(context, PlayerActivity::class.java).apply {
                                        putExtra(PlayerActivity.EXTRA_LIVE, true)
                                        putExtra(PlayerActivity.EXTRA_TITLE, ch.name)
                                        putExtra(PlayerActivity.EXTRA_LIVE_URL, ch.urls.first())
                                        putStringArrayListExtra(
                                            PlayerActivity.EXTRA_LIVE_URLS,
                                            ArrayList(ch.urls),
                                        )
                                    }
                                )
                            },
                        )
                        HorizontalDivider(
                            color = AppColors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 70.dp),
                        )
                    }
                }
            }
        }
    }
}
