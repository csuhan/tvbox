package com.juying.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.juying.tv.ui.components.BrandBar
import com.juying.tv.ui.theme.*

@Composable
fun SettingsScreen(nav: NavHostController, mainVm: MainViewModel = viewModel()) {
    val configState by mainVm.configState.collectAsState()
    val configUrl by mainVm.configUrl.collectAsState()
    val mySources by mainVm.sources.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            Text("设置", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }

        // 当前接口状态
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("当前接口", color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardDark).padding(14.dp)
            ) {
                Text(configUrl, color = TextPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val status = when (val s = configState) {
                    is ConfigState.Loading -> "⏳ 加载中…"
                    is ConfigState.Success -> "✅ ${s.config.sites.size} 个站点 · ${s.config.lives.size} 个直播源 · ${s.config.parses.size} 个解析"
                    is ConfigState.Error -> "❌ ${s.message}"
                    else -> ""
                }
                Text(status, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = { mainVm.loadConfig(configUrl) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("重新加载", color = Primary, fontSize = 13.sp)
                }
            }
        }

        // 我的接口
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我的接口", color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                Text(" 添加", color = Primary, fontSize = 13.sp)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mySources.isEmpty()) {
                Text("还没有保存的接口，从下方推荐添加或手动输入", color = TextSecondary, fontSize = 12.sp)
            }
            mySources.forEach { src ->
                SourceRow(src, active = src.url == configUrl,
                    onUse = { mainVm.loadConfig(src.url) },
                    onDelete = { mainVm.removeSource(src.url) })
            }
        }

        // 推荐接口
        Text("推荐接口", color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 8.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceManager.recommended.forEach { src ->
                val saved = mySources.any { it.url == src.url }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardDark)
                        .clickable { mainVm.loadConfig(src.url) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(src.name, color = TextPrimary, fontSize = 14.sp)
                        Text(src.url, color = TextSecondary, fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    if (src.url == configUrl) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "使用中", tint = Primary,
                            modifier = Modifier.size(18.dp))
                    } else if (!saved) {
                        TextButton(onClick = { mainVm.addSource(src.name, src.url) }) {
                            Text("收藏", color = Secondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 关于
        Text("关于", color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 8.dp))
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).background(CardDark).padding(14.dp),
        ) {
            Text("聚影 v1.0.0", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "支持 TVBox 接口格式（苹果CMS 采集 API）的点播与直播聚合播放器。\n仅供学习交流，内容来自第三方公开接口。",
                color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(32.dp).navigationBarsPadding())
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = SurfaceDark,
            title = { Text("添加接口", color = TextPrimary) },
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
                }) { Text("添加并使用", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消", color = TextSecondary) }
            },
        )
    }
}

@Composable
private fun SourceRow(src: SourceEntry, active: Boolean, onUse: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (active) PrimarySoft else CardDark)
            .clickable(onClick = onUse)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(src.name, color = if (active) Primary else TextPrimary, fontSize = 14.sp)
            Text(src.url, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (active) {
            Icon(Icons.Default.CheckCircle, contentDescription = "使用中", tint = Primary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
