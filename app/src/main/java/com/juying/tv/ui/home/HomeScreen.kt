package com.juying.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.juying.tv.data.model.Site
import com.juying.tv.data.model.VodClass
import com.juying.tv.data.source.ConfigState
import com.juying.tv.ui.HomeViewModel
import com.juying.tv.ui.MainViewModel
import com.juying.tv.ui.components.*
import com.juying.tv.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController, mainVm: MainViewModel) {
    val vm: HomeViewModel = viewModel()
    val site by mainVm.currentSite.collectAsState()
    val reset by mainVm.homeReset.collectAsState()
    val state by vm.state.collectAsState()

    LaunchedEffect(site?.key, reset) { vm.bind(site) }

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {
        // 顶区：纤细品牌行（小标识 + 搜索图标）+ 分类 Tab
        // 外层 Box 负责白色背景（含状态栏区域），内层 padding 只负责内容下移
        Box(Modifier.fillMaxWidth().background(AppColors.card)) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(AppColors.accent)
                )
                Text(
                    "聚影",
                    color = AppColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { nav.navigate("search") }) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "搜索",
                        tint = AppColors.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            ClassTabs(state.classes, state.selectedType) { vm.selectClass(it) }
        }
        }
        HorizontalDivider(color = AppColors.divider, thickness = 0.5.dp)

        when {
            state.error != null && state.items.isEmpty() -> ErrorView(state.error!!) { vm.refresh() }
            state.loading && state.items.isEmpty() -> LoadingView()
            else -> {
                val gridState = rememberLazyGridState()
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val info = gridState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= info.totalItemsCount - 6
                    }
                }
                LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMore() }

                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = { vm.refresh() },
                ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = state.items.size,
                        key = { i -> "${state.items[i].vodId}_$i" },
                    ) { i ->
                        val item = state.items[i]
                        VodCard(item, onClick = { nav.navigate("detail/${item.vodId}") })
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = AppColors.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

/** 分类 Tab：material3 ScrollableTabRow（官方指示器动画） */
@Composable
private fun ClassTabs(classes: List<VodClass>, selected: String?, onSelect: (String?) -> Unit) {
    val selectedIndex = (classes.indexOfFirst { it.typeId == selected } + 1).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        edgePadding = 12.dp,
        divider = {},
        indicator = { positions ->
            if (selectedIndex < positions.size) {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[selectedIndex]),
                    color = AppColors.accent,
                )
            }
        },
    ) {
        Tab(selected = selected == null, onClick = { onSelect(null) }) {
            TabText("推荐", selected == null)
        }
        classes.forEach { c ->
            Tab(selected = selected == c.typeId, onClick = { onSelect(c.typeId) }) {
                TabText(c.typeName, selected == c.typeId)
            }
        }
    }
}

@Composable
private fun TabText(text: String, selected: Boolean) {
    Text(
        text,
        color = if (selected) AppColors.accent else AppColors.textSecondary,
        fontSize = if (selected) 15.sp else 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.padding(vertical = 10.dp),
    )
}

@Composable
fun SiteSheet(sites: List<Site>, current: Site?, onSelect: (Site) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        Text(
            "选择站点",
            color = AppColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        val supported = sites.filter { it.isSupported }
        val unsupported = sites.filter { !it.isSupported }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("可用站点 · ${supported.size}", color = AppColors.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            supported.forEach { s ->
                SiteRow(s, current?.key == s.key) { onSelect(s) }
            }
            if (unsupported.isNotEmpty()) {
                Text(
                    "爬虫源（当前版本暂不支持）· ${unsupported.size}",
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
                unsupported.take(20).forEach { s ->
                    Text(
                        s.name,
                        color = AppColors.textSecondary.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteRow(site: Site, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            site.name,
            color = if (selected) AppColors.accent else AppColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("当前", color = AppColors.accent, fontSize = 12.sp)
        }
    }
}
