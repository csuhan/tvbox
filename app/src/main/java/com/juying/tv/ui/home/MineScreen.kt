package com.juying.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.juying.tv.ui.LibraryViewModel
import com.juying.tv.ui.components.BrandBar
import com.juying.tv.ui.theme.*

@Composable
fun MineScreen(nav: NavHostController) {
    val vm: LibraryViewModel = viewModel()
    val history by vm.history.collectAsState()
    val favorites by vm.favorites.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        BrandBar(title = "我的")

        // 功能入口
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuRow("⚙️", "设置与数据源", "管理接口、站点与偏好") { nav.navigate("settings") }
            MenuRow("🗑", "清空观看历史", "共 ${history.size} 条记录") { showClearConfirm = true }
        }

        // 观看历史
        SectionTitle("观看历史", if (history.isEmpty()) "暂无记录" else "继续观看")
        if (history.isEmpty()) {
            Text("看过的影片会出现在这里", color = TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(history.take(30), key = { it.siteKey + it.vodId }) { h ->
                    Column(
                        Modifier.width(104.dp).clickable { nav.navigate("detail/${h.vodId}") },
                    ) {
                        Box {
                            AsyncImage(
                                model = h.vodPic, contentDescription = h.vodName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth().height(146.dp)
                                    .clip(RoundedCornerShape(10.dp)).background(CardDark),
                            )
                            if (h.duration > 0) {
                                val p = (h.position.toFloat() / h.duration).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { p },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                        .fillMaxWidth().padding(horizontal = 6.dp).padding(bottom = 6.dp)
                                        .height(3.dp).clip(RoundedCornerShape(2.dp)),
                                    color = Accent,
                                    trackColor = androidx.compose.ui.graphics.Color(0x66FFFFFF),
                                )
                            }
                        }
                        Text(h.vodName, color = TextPrimary, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                        Text(h.episodeName, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // 收藏
        SectionTitle("我的收藏", "${favorites.size} 部")
        if (favorites.isEmpty()) {
            Text("收藏的影片会出现在这里", color = TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(favorites.take(30), key = { it.siteKey + it.vodId }) { f ->
                    Column(Modifier.width(104.dp).clickable { nav.navigate("detail/${f.vodId}") }) {
                        AsyncImage(
                            model = f.vodPic, contentDescription = f.vodName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth().height(146.dp)
                                .clip(RoundedCornerShape(10.dp)).background(CardDark),
                        )
                        Text(f.vodName, color = TextPrimary, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = SurfaceDark,
            title = { Text("清空历史", color = TextPrimary) },
            text = { Text("确定要清空全部观看历史吗？", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); showClearConfirm = false }) {
                    Text("清空", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消", color = TextSecondary) }
            },
        )
    }
}

@Composable
private fun MenuRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))
        Text(subtitle, color = TextSecondary, fontSize = 12.sp)
    }
}
