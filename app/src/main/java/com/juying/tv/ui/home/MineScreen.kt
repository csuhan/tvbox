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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Settings
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
import coil.compose.SubcomposeAsyncImage
import com.juying.tv.ui.LibraryViewModel
import com.juying.tv.ui.components.BrandBar
import com.juying.tv.ui.components.SectionHeader
import com.juying.tv.ui.theme.AppColors

@Composable
fun MineScreen(nav: NavHostController) {
    val vm: LibraryViewModel = viewModel()
    val history by vm.history.collectAsState()
    val favorites by vm.favorites.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.fillMaxWidth().background(AppColors.card)) {
            Spacer(Modifier.statusBarsPadding())
            BrandBar(title = "我的")
        }

        // 功能入口（分组卡片 + ListItem）
        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.card),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            ListItem(
                headlineContent = { Text("设置与数据源") },
                supportingContent = { Text("管理接口、站点与偏好") },
                leadingContent = { Icon(Icons.Outlined.Settings, null, tint = AppColors.accent) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AppColors.textSecondary)
                },
                colors = ListItemDefaults.colors(containerColor = AppColors.card),
                modifier = Modifier.clickable { nav.navigate("settings") },
            )
            HorizontalDivider(color = AppColors.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
            ListItem(
                headlineContent = { Text("清空观看历史") },
                supportingContent = { Text("共 ${history.size} 条记录") },
                leadingContent = { Icon(Icons.Outlined.DeleteOutline, null, tint = AppColors.textSecondary) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AppColors.textSecondary)
                },
                colors = ListItemDefaults.colors(containerColor = AppColors.card),
                modifier = Modifier.clickable { showClearConfirm = true },
            )
        }

        // 观看历史
        SectionHeader("观看历史", if (history.isEmpty()) "暂无记录" else "${history.size} 条")
        if (history.isEmpty()) {
            Text(
                "看过的影片会出现在这里",
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(history.take(30), key = { it.siteKey + it.vodId }) { h ->
                    Column(Modifier.width(104.dp).clickable { nav.navigate("detail/${h.vodId}") }) {
                        Box {
                            SubcomposeAsyncImage(
                                model = h.vodPic,
                                contentDescription = h.vodName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(146.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppColors.fill),
                            )
                            if (h.duration > 0) {
                                val p = (h.position.toFloat() / h.duration).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { p },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp)
                                        .padding(bottom = 6.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = AppColors.accent,
                                    trackColor = androidx.compose.ui.graphics.Color(0x66FFFFFF),
                                )
                            }
                        }
                        Text(
                            h.vodName,
                            color = AppColors.textPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                        Text(h.episodeName, color = AppColors.textSecondary, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // 收藏
        SectionHeader("我的收藏", "${favorites.size} 部")
        if (favorites.isEmpty()) {
            Text(
                "收藏的影片会出现在这里",
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(favorites.take(30), key = { it.siteKey + it.vodId }) { f ->
                    Column(Modifier.width(104.dp).clickable { nav.navigate("detail/${f.vodId}") }) {
                        SubcomposeAsyncImage(
                            model = f.vodPic,
                            contentDescription = f.vodName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(146.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppColors.fill),
                        )
                        Text(
                            f.vodName,
                            color = AppColors.textPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = AppColors.card,
            title = { Text("清空历史", color = AppColors.textPrimary) },
            text = { Text("确定要清空全部观看历史吗？", color = AppColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); showClearConfirm = false }) {
                    Text("清空", color = AppColors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消", color = AppColors.textSecondary) }
            },
        )
    }
}
