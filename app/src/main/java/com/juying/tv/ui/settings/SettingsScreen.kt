package com.juying.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.juying.tv.data.source.ConfigState
import com.juying.tv.data.source.SourceEntry
import com.juying.tv.data.source.SourceManager
import com.juying.tv.ui.MainViewModel
import com.juying.tv.ui.components.SectionHeader
import com.juying.tv.ui.theme.AppColors

@Composable
fun SettingsScreen(nav: NavHostController, mainVm: MainViewModel = viewModel()) {
    val configState by mainVm.configState.collectAsState()
    val configUrl by mainVm.configUrl.collectAsState()
    val mySources by mainVm.sources.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .background(AppColors.card)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = AppColors.textPrimary)
            }
            Text("设置", color = AppColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        // 当前接口
        SectionHeader("当前接口")
        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.card),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            ListItem(
                headlineContent = {
                    Text(configUrl, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    val status = when (val s = configState) {
                        is ConfigState.Loading -> "加载中…"
                        is ConfigState.Success -> "✓ ${s.config.sites.size} 个站点 · ${s.config.lives.size} 个直播源 · ${s.config.parses.size} 个解析"
                        is ConfigState.Error -> "加载失败：${s.message}"
                        else -> ""
                    }
                    Text(
                        status,
                        color = if (configState is ConfigState.Error) AppColors.accent else AppColors.textSecondary,
                        fontSize = 12.sp,
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Link, null, tint = AppColors.accent) },
                trailingContent = {
                    IconButton(onClick = { mainVm.loadConfig(configUrl) }) {
                        Icon(Icons.Outlined.Refresh, "重新加载", tint = AppColors.textSecondary)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = AppColors.card),
            )
        }

        // 我的接口
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { SectionHeader("我的接口") }
            TextButton(onClick = { showAdd = true }) {
                Icon(Icons.Outlined.Add, null, tint = AppColors.accent, modifier = Modifier.size(16.dp))
                Text(" 添加", color = AppColors.accent, fontSize = 13.sp)
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.card),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            if (mySources.isEmpty()) {
                Text(
                    "还没有保存的接口，从下方推荐添加或手动输入",
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            mySources.forEachIndexed { i, src ->
                if (i > 0) HorizontalDivider(color = AppColors.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SourceRow(src, active = src.url == configUrl,
                    onUse = { mainVm.loadConfig(src.url) },
                    onDelete = { mainVm.removeSource(src.url) })
            }
        }

        // 推荐接口
        SectionHeader("推荐接口")
        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.card),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            SourceManager.recommended.forEachIndexed { i, src ->
                if (i > 0) HorizontalDivider(color = AppColors.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                val saved = mySources.any { it.url == src.url }
                ListItem(
                    headlineContent = { Text(src.name, fontSize = 14.sp) },
                    supportingContent = {
                        Text(src.url, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = {
                        if (src.url == configUrl) {
                            Icon(Icons.Outlined.CheckCircle, "使用中", tint = AppColors.accent)
                        } else {
                            Icon(Icons.Outlined.Link, null, tint = AppColors.textSecondary)
                        }
                    },
                    trailingContent = {
                        if (src.url != configUrl && !saved) {
                            TextButton(onClick = { mainVm.addSource(src.name, src.url) }) {
                                Text("收藏", color = AppColors.accent, fontSize = 12.sp)
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = AppColors.card),
                    modifier = Modifier.clickable { mainVm.loadConfig(src.url) },
                )
            }
        }

        // 关于
        SectionHeader("关于")
        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.card),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            ListItem(
                headlineContent = { Text("聚影 v1.0.0") },
                supportingContent = {
                    Text(
                        "支持 TVBox 接口格式（苹果CMS 采集 API）的点播与直播聚合播放器。仅供学习交流，内容来自第三方公开接口。",
                        fontSize = 12.sp, lineHeight = 18.sp,
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Info, null, tint = AppColors.textSecondary) },
                colors = ListItemDefaults.colors(containerColor = AppColors.card),
            )
        }
        Spacer(Modifier.height(32.dp).navigationBarsPadding())
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = AppColors.card,
            title = { Text("添加接口", color = AppColors.textPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("名称（可选）") }, singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("接口地址 http(s)://…") }, singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (url.isNotBlank()) {
                        mainVm.addSource(name, url.trim())
                        mainVm.loadConfig(url.trim())
                        showAdd = false
                    }
                }) { Text("添加并使用", color = AppColors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消", color = AppColors.textSecondary) }
            },
        )
    }
}

@Composable
private fun SourceRow(src: SourceEntry, active: Boolean, onUse: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                src.name,
                fontSize = 14.sp,
                color = if (active) AppColors.accent else AppColors.textPrimary,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
        },
        supportingContent = {
            Text(src.url, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            if (active) Icon(Icons.Outlined.CheckCircle, "使用中", tint = AppColors.accent)
            else Icon(Icons.Outlined.Link, null, tint = AppColors.textSecondary)
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, "删除", tint = AppColors.textSecondary)
            }
        },
        colors = ListItemDefaults.colors(containerColor = AppColors.card),
        modifier = Modifier.clickable(onClick = onUse),
    )
}
