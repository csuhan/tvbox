package com.juying.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
import com.juying.tv.ui.components.HomeCard
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
        }
        }

        when {
            state.bootError != null -> ErrorView(state.bootError!!) { vm.bind(site) }
            state.bootLoading -> LoadingView()
            else -> {
                val pageCount = state.classes.size + 1
                val pagerState = rememberPagerState(pageCount = { pageCount })
                val scope = rememberCoroutineScope()

                // Pager 翻页 → 确保该分类已加载
                LaunchedEffect(pagerState.currentPage, state.classes) {
                    val tid = if (pagerState.currentPage == 0) ""
                    else state.classes.getOrNull(pagerState.currentPage - 1)?.typeId ?: return@LaunchedEffect
                    vm.ensureLoaded(tid)
                }
                // 换站点 → 回到第一页
                LaunchedEffect(site?.key) {
                    if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
                }

                Column(Modifier.fillMaxSize()) {
                    CategoryTabRow(
                        classes = state.classes,
                        selectedIndex = pagerState.currentPage,
                        onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                    ) { pageIndex ->
                        val typeId = if (pageIndex == 0) "" else state.classes[pageIndex - 1].typeId
                        CategoryPage(
                            page = state.pages[typeId],
                            isHome = typeId.isEmpty(),
                            onRefresh = { vm.refreshClass(typeId) },
                            onLoadMore = { vm.loadMore(typeId) },
                            onItemClick = { item -> nav.navigate("detail/${item.vodId}") },
                        )
                    }
                }
            }
        }
    }
}

/** 分类 Tab：material3 ScrollableTabRow，与 Pager 双向同步 */
@Composable
private fun CategoryTabRow(classes: List<VodClass>, selectedIndex: Int, onSelect: (Int) -> Unit) {
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
        Tab(selected = selectedIndex == 0, onClick = { onSelect(0) }) {
            TabText("推荐", selectedIndex == 0)
        }
        classes.forEachIndexed { i, c ->
            Tab(selected = selectedIndex == i + 1, onClick = { onSelect(i + 1) }) {
                TabText(c.typeName, selectedIndex == i + 1)
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

/** 单个分类页：下拉刷新 + 网格 + 触底加载 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPage(
    page: HomeViewModel.ClassPage?,
    isHome: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (com.juying.tv.data.model.VodItem) -> Unit,
) {
    when {
        page == null || (page.loading && page.items.isEmpty()) -> LoadingView()
        page.error != null && page.items.isEmpty() -> ErrorView(page.error!!, onRefresh)
        else -> {
            val gridState = rememberLazyGridState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val info = gridState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= info.totalItemsCount - 6
                }
            }
            LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

            PullToRefreshBox(isRefreshing = page.loading, onRefresh = onRefresh) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 推荐页顶部：Hero 轮播（芒果风）
                    if (isHome && page.items.size >= 5) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            HeroBanner(
                                items = page.items.take(5),
                                onItemClick = onItemClick,
                            )
                        }
                    }
                    items(
                        count = page.items.size,
                        key = { i -> "${page.items[i].vodId}_$i" },
                    ) { i ->
                        HomeCard(page.items[i], onClick = { onItemClick(page.items[i]) })
                    }
                    if (page.loadingMore) {
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
        // 按来源接口分组（聚合模式）
        val sourceOf = com.juying.tv.data.source.SourceManager.siteSourceName
        val grouped = supported.groupBy { sourceOf[it.key] ?: "默认接口" }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            grouped.forEach { (sourceName, list) ->
                Text(
                    "$sourceName · ${list.size} 站点",
                    color = AppColors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
                list.forEach { s ->
                    SiteRow(s, current?.key == s.key) { onSelect(s) }
                }
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


/** 芒果风 Hero 轮播：大图 + 渐变标题 + 指示点 + 4s 自动滚动 */
@Composable
private fun HeroBanner(
    items: List<com.juying.tv.data.model.VodItem>,
    onItemClick: (com.juying.tv.data.model.VodItem) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    LaunchedEffect(items) {
        while (true) {
            kotlinx.coroutines.delay(4000)
            if (items.isNotEmpty()) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }
    Box(
        Modifier
            .padding(top = 4.dp, bottom = 6.dp)
            .fillMaxWidth()
            .aspectRatio(1.9f)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.fill),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { i ->
            val item = items[i]
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { onItemClick(item) },
            ) {
                val placeholder = androidx.compose.ui.graphics.painter.ColorPainter(AppColors.fill)
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(item.vodPic)
                        .size(900, 480)
                        .crossfade(false)
                        .build(),
                    contentDescription = item.vodName,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                    fallback = placeholder,
                    modifier = Modifier.fillMaxSize(),
                )
                // 底部渐变 + 标题
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.ui.graphics.Color(0xB3000000),
                                )
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Column {
                        if (item.vodRemarks.isNotBlank()) {
                            Text(
                                item.vodRemarks,
                                color = AppColors.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            item.vodName,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // 指示点
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(items.size) { i ->
                Box(
                    Modifier
                        .size(if (i == pagerState.currentPage) 7.dp else 5.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (i == pagerState.currentPage) AppColors.accent
                            else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
                        ),
                )
            }
        }
    }
}
