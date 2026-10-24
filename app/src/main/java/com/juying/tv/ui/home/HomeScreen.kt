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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
    val configState by mainVm.configState.collectAsState()
    val site by mainVm.currentSite.collectAsState()
    val reset by mainVm.homeReset.collectAsState()
    val state by vm.state.collectAsState()
    var showSiteSheet by remember { mutableStateOf(false) }

    LaunchedEffect(site?.key, reset) { vm.bind(site) }

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {
        // 白色顶区：品牌栏 + 搜索框
        Column(Modifier.fillMaxWidth().background(AppColors.card)) {
            Spacer(Modifier.statusBarsPadding())
            BrandBar(
                title = "聚影",
                subtitle = site?.name,
            ) {
                IconButton(onClick = { nav.navigate("settings") }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = AppColors.textSecondary)
                }
            }
            // 搜索入口（浅灰填充，B站风）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.fill)
                    .clickable { nav.navigate("search") }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = AppColors.textSecondary,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    "  搜索影片、剧集、综艺…",
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        // 换源入口（细文字按钮）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.card)
                .clickable { showSiteSheet = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "当前站点：${site?.name ?: "未选择"}",
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (site?.isSupported == false) {
                Text("爬虫源暂不支持  ", color = AppColors.accent, fontSize = 12.sp)
            }
            Text("换源", color = AppColors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = AppColors.accent,
                modifier = Modifier.size(15.dp),
            )
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
                    // B站风文字分类 Tab
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ClassTabs(state.classes, state.selectedType) { vm.selectClass(it) }
                    }
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

    if (showSiteSheet) {
        val cfg = (configState as? ConfigState.Success)?.config
        ModalBottomSheet(
            onDismissRequest = { showSiteSheet = false },
            containerColor = AppColors.card,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            SiteSheet(
                sites = cfg?.sites ?: emptyList(),
                current = site,
                onSelect = { mainVm.selectSite(it); showSiteSheet = false },
            )
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
