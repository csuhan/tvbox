package com.juying.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.juying.tv.ui.MainViewModel
import com.juying.tv.ui.SearchViewModel
import com.juying.tv.ui.components.*
import com.juying.tv.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(nav: NavHostController, mainVm: MainViewModel = viewModel()) {
    val vm: SearchViewModel = viewModel()
    val site by mainVm.currentSite.collectAsState()
    val state by vm.state.collectAsState()
    var keyword by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { delay(300); focus.requestFocus() }
    // 防抖搜索
    LaunchedEffect(keyword) {
        if (keyword.isBlank()) return@LaunchedEffect
        delay(500)
        vm.search(site, keyword)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // 搜索栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardDark)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(Primary),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).focusRequester(focus),
                    decorationBox = { inner ->
                        if (keyword.isEmpty()) Text("输入片名搜索…", color = TextSecondary, fontSize = 14.sp)
                        inner()
                    },
                )
                if (keyword.isNotEmpty()) {
                    Icon(Icons.Default.Close, contentDescription = "清空", tint = TextSecondary,
                        modifier = Modifier.size(16.dp).clickable { keyword = "" })
                }
            }
        }

        when {
            keyword.isBlank() -> {
                // 热门搜索
                Column(Modifier.padding(16.dp)) {
                    Text("🔥 热门搜索", color = TextPrimary, fontSize = 15.sp)
                    FlowRowWrap(vm.hotWords) { word ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardDark)
                                .clickable { keyword = word }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text(word, color = TextSecondary, fontSize = 13.sp) }
                    }
                }
            }
            state.loading -> LoadingView("搜索中…")
            state.error != null -> ErrorView(state.error!!) { vm.search(site, keyword) }
            state.results.isEmpty() && state.searched -> EmptyView("没有找到「$keyword」相关内容")
            else -> {
                Text(
                    "共 ${state.results.size} 条结果",
                    color = TextSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        count = state.results.size,
                        key = { i -> "${state.results[i].vodId}_$i" },
                    ) { i ->
                        val item = state.results[i]
                        VodCard(item, onClick = { nav.navigate("detail/${item.vodId}") })
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowRowWrap(items: List<String>, chip: @Composable (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        items.forEach { chip(it) }
    }
}
