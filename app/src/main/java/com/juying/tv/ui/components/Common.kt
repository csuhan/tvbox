package com.juying.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import com.juying.tv.data.model.VodItem
import com.juying.tv.ui.theme.AppColors

/** B站风海报卡片：圆角封面 + 右上角角标 + 双行标题 */
@Composable
fun VodCard(item: VodItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.fill)
        ) {
            if (item.vodPic.isBlank()) {
                // 无封面：极简占位
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        tint = AppColors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(34.dp),
                    )
                }
            } else {
                // 性能优化：静态占位 + 按卡片尺寸解码 + 无 crossfade，滑动不掉帧
                val placeholder = ColorPainter(AppColors.fill)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.vodPic)
                        .size(360, 500)
                        .crossfade(false)
                        .build(),
                    contentDescription = item.vodName,
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                    fallback = placeholder,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // 右上角标（B站风）
            if (item.vodRemarks.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        item.vodRemarks,
                        color = Color.White,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            item.vodName,
            color = AppColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp, start = 1.dp, end = 1.dp),
        )
    }
}

/** 芒果风两列大卡：横幅图 + 左下备注(渐变) + 右下橙标 + 双行标题 */
@Composable
fun HomeCard(item: VodItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.80f)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.fill)
        ) {
            val placeholder = ColorPainter(AppColors.fill)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.vodPic)
                    .size(540, 680)
                    .crossfade(false)
                    .build(),
                contentDescription = item.vodName,
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder,
                modifier = Modifier.fillMaxSize(),
            )
            // 左下：备注（黑色渐变底）
            if (item.vodRemarks.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x99000000))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        item.vodRemarks,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 右下：橙色类型标
            if (item.typeName.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.accent)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        item.typeName,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(
            item.vodName,
            color = AppColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/** 加载中 */
@Composable
fun LoadingView(text: String = "加载中…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = AppColors.accent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

/** 错误页 */
@Composable
fun ErrorView(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppColors.fill),
                contentAlignment = Alignment.Center,
            ) {
                Text(":(", color = AppColors.textSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                message,
                color = AppColors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  重试", fontSize = 14.sp)
                }
            }
        }
    }
}

/** 空状态 */
@Composable
fun EmptyView(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppColors.fill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    tint = AppColors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(text, color = AppColors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/** 顶部品牌栏：文字标 + 点缀，右侧操作 */
@Composable
fun BrandBar(title: String, subtitle: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = AppColors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Box(
                Modifier
                    .padding(start = 3.dp, bottom = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AppColors.accent)
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
                )
            }
        }
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** 分组标题（左侧竖线 + 标题，B站分区风） */
@Composable
fun SectionHeader(title: String, extra: String? = null, onMore: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppColors.accent)
        )
        Text(
            title,
            color = AppColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        if (extra != null) {
            Text(extra, color = AppColors.textSecondary, fontSize = 12.sp)
        }
        if (onMore != null) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = AppColors.textSecondary,
                modifier = Modifier.size(14.dp).clickable(onClick = onMore),
            )
        }
    }
}
