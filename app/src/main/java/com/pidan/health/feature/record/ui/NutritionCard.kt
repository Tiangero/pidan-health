package com.pidan.health.feature.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
        Text(
            text = "${row.intakeText} / ${row.targetText}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(row.status.labelRes()),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(row.status.statusContainerColor())
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun com.pidan.health.core.domain.NutritionStatus.labelRes(): Int = when (this) {
    com.pidan.health.core.domain.NutritionStatus.UNDER -> R.string.status_under
    com.pidan.health.core.domain.NutritionStatus.OK -> R.string.status_ok
    com.pidan.health.core.domain.NutritionStatus.OVER -> R.string.status_over
}
