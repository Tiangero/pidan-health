package com.pidan.health.feature.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.DashboardCard
import com.pidan.health.core.designsystem.component.NutritionStatusColors
import com.pidan.health.core.designsystem.component.SegmentedToggle
import com.pidan.health.feature.record.viewmodel.RecordUiState
import com.pidan.health.feature.record.viewmodel.WeightChartRange

/**
 * 体重卡片：当前体重 + 编辑入口 + 走势折线（无数据显示空，单点只画点）
 * + 「近7天/近30天」官方分段开关 + 区间增减值（VM 算好，UI 只着色）。
 */
@Composable
fun WeightCard(
    state: RecordUiState,
    onEditWeightClick: () -> Unit,
    onChartRangeChange: (WeightChartRange) -> Unit,
    modifier: Modifier = Modifier
) {
    DashboardCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.record_weight_title),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onEditWeightClick) {
                Text(text = stringResource(R.string.action_edit))
            }
        }

        Text(
            text = state.todayWeight?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "--",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.unit_kg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val weights = if (state.chartRange == WeightChartRange.D7) state.weights7 else state.weights30
        if (weights.isNotEmpty()) {
            WeightTrendChart(
                weights = weights,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        // 分段开关在左；右侧增减值只在区间有 ≥2 条真实记录时出现（VM 计算，无预置数据）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegmentedToggle(
                options = listOf(
                    stringResource(R.string.chart_7d),
                    stringResource(R.string.chart_30d)
                ),
                selectedIndex = if (state.chartRange == WeightChartRange.D7) 0 else 1,
                onSelect = { index ->
                    onChartRangeChange(
                        if (index == 0) WeightChartRange.D7 else WeightChartRange.D30
                    )
                },
                modifier = Modifier
                    .width(168.dp)
                    .height(32.dp)
            )
            state.weightDelta?.let { delta ->
                Text(
                    text = delta.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (delta.direction) {
                        -1 -> NutritionStatusColors.Ok
                        1 -> NutritionStatusColors.Over
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
