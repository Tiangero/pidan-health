package com.pidan.health.feature.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.DashboardCard
import com.pidan.health.core.designsystem.component.NutrientRing
import com.pidan.health.core.designsystem.component.statusColor
import com.pidan.health.core.designsystem.component.statusContainerColor
import com.pidan.health.feature.record.viewmodel.NutrientRowUi
import com.pidan.health.feature.record.viewmodel.RecordUiState

/**
 * 营养卡片：5 大营养素行式布局（环形进度 + 名称 + 已摄入/目标值 + 状态）。
 * 着色严格由 T3 判定引擎输出的 status 决定，UI 层无任何计算（红线 2）。
 */
@Composable
fun NutritionCard(
    state: RecordUiState,
    modifier: Modifier = Modifier
) {
    DashboardCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.record_nutrition_title),
            style = MaterialTheme.typography.titleMedium
        )

        if (state.nutrientRows.isEmpty()) {
            Text(
                text = stringResource(R.string.nutrition_empty_hint),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@DashboardCard
        }

        state.nutrientRows.forEach { row ->
            NutrientRow(
                row = row,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun NutrientRow(
    row: NutrientRowUi,
    modifier: Modifier = Modifier
) {
    val color = row.status.statusColor()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NutrientRing(
            progress = row.progress,
            color = color,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = stringResource(row.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.intakeText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                // 摄入列 3 位数字空间，数据居中（2026-10-11 最终拍板）
                textAlign = TextAlign.Center,
                modifier = Modifier.width(INTAKE_COLUMN_WIDTH)
            )
            Text(
                text = "/",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            // 目标数字单独一个 3 位数字模块，同样居中；「克」固定渲染在右侧
            Text(
                text = row.targetText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(INTAKE_COLUMN_WIDTH)
            )
            Text(
                text = stringResource(R.string.unit_gram),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = stringResource(row.status.labelRes()),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
            // 固定宽度（容纳「未达标」3 字）+ 居中：标签宽度不再随文案变化，
            // 否则「达标」窄一截，左侧克数展示会跟着左右漂移（2026-10-11 用户反馈）
            modifier = Modifier
                .width(STATUS_PILL_WIDTH)
                .clip(RoundedCornerShape(6.dp))
                .background(row.status.statusContainerColor())
                .padding(vertical = 3.dp)
        )
    }
}

private fun com.pidan.health.core.domain.NutritionStatus.labelRes(): Int = when (this) {
    com.pidan.health.core.domain.NutritionStatus.UNDER -> R.string.status_under
    com.pidan.health.core.domain.NutritionStatus.OK -> R.string.status_ok
    com.pidan.health.core.domain.NutritionStatus.OVER -> R.string.status_over
}

/** 数字列：14sp 下 3 位数字宽度，摄入/目标数字共用，数据居中（2026-10-11 最终拍板） */
private val INTAKE_COLUMN_WIDTH = 28.dp

/** 状态标签固定宽度：11sp 下容纳「未达标」3 字并留余量，五行标签右边缘严格对齐 */
private val STATUS_PILL_WIDTH = 52.dp
