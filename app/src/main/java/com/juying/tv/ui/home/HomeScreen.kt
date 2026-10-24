package com.juying.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
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
import com.juying.tv.data.source.ConfigState
import com.juying.tv.ui.HomeViewModel
import com.juying.tv.ui.MainViewModel
import com.juying.tv.ui.components.*
import com.juying.tv.ui.theme.*

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

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BrandBar(
            title = "聚影",
            subtitle = site?.name ?: "未选择站点",
        ) {
            IconButton(onClick = { nav.navigate("settings") }) {
                Text("⚙", fontSize = 20.sp, color = TextSecondary)
            }
        }

        // 搜索入口
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .clickable { nav.navigate("search") }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Text("  搜索影片、剧集、综艺…", color = TextSecondary, fontSize = 14.sp)
        }

        // 换源按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(PrimarySoft)
                    .clickable { showSiteSheet = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "📡 ${site?.name ?: "选择站点"}",
                    color = Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
            }
            if (site != null && site?.isSupported == false) {
                Text("  爬虫源暂不支持", color = Accent, fontSize = 12.sp)
            }
        }

        when {
            state.error != null -> ErrorView(state.error!!) { vm.refresh() }
            state.loading -> LoadingView()
            else -> {
                val gridState = rememberLazyGridState()
                // 触底加载更多
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val info = gridState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= info.totalItemsCount - 6
                    }
                }
                LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMore() }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 分类行
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ClassTabs(state.classes, state.selectedType) { vm.selectClass(it) }
                    }
                    items(state.items, key = { it.vodId + it.vodName }) { item ->
                        VodCard(item, onClick = { nav.navigate("detail/${item.vodId}") })
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Primary, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // 站点选择
    if (showSiteSheet) {
        val cfg = (configState as? ConfigState.Success)?.config
        ModalBottomSheet(
            onDismissRequest = { showSiteSheet = false },
            containerColor = SurfaceDark,
        ) {
            SiteSheet(
                sites = cfg?.sites ?: emptyList(),
                current = site,
                onSelect = { mainVm.selectSite(it); showSiteSheet = false },
            )
        }
    }
}

@Composable
private fun ClassTabs(classes: List<com.juying.tv.data.model.VodClass>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClassChip("推荐", selected == null) { onSelect(null) }
        classes.forEach { c ->
            ClassChip(c.typeName, selected == c.typeId) { onSelect(c.typeId) }
        }
    }
}

@Composable
private fun ClassChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Primary else CardDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            color = if (selected) androidx.compose.ui.graphics.Color.White else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun SiteSheet(sites: List<Site>, current: Site?, onSelect: (Site) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text("选择站点", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 10.dp))
        val supported = sites.filter { it.isSupported }
        val unsupported = sites.filter { !it.isSupported }
        Text("可用站点（${supported.size}）", color = Secondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            supported.forEach { s ->
                SiteRow(s, current?.key == s.key) { onSelect(s) }
            }
            if (unsupported.isNotEmpty()) {
                Text("爬虫源（暂不支持，${unsupported.size}）", color = TextSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                unsupported.take(20).forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardDark.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(s.name, color = TextSecondary.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteRow(site: Site, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PrimarySoft else CardDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(site.name, color = if (selected) Primary else TextPrimary, fontSize = 14.sp,
            modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
    }
}
