package com.pidan.health.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pidan.health.core.designsystem.theme.BrandOrange

/**
 * 通用顶栏（对齐设计稿 .appbar）：白底（含状态栏区域）+ 底部 1px 分隔线，左侧标题、右侧操作区。
 * 记录页用 [BrandLogoTitle] 做 logo 标题；其余页面传纯文本标题。
 */
@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // 固定 48dp：右侧 TextButton 自带 40dp 最小高度，不固定会被 actions 内容撑得忽高忽低
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            title()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                content = actions
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/** 品牌 logo 标题：「皮蛋」橙色 +「健康」墨色（设计稿 appbar .title em） */
@Composable
fun BrandLogoTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "皮蛋",
            color = BrandOrange,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "健康",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 普通页面文本标题（设计稿 appbar .title：17px 粗体），与 BrandLogoTitle 视觉一致 */
@Composable
fun AppTopBarTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold
    )
}
