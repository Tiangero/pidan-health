package com.pidan.health.feature.record.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.DashboardCard
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.feature.record.viewmodel.RecordUiState

/**
 * 开销卡片：当日累计金额大号展示 + 对比本周日均值。
 */
@Composable
fun CostCard(
    state: RecordUiState,
    modifier: Modifier = Modifier
) {
    DashboardCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.record_cost_title),
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = state.todayCostText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = BrandOrangeDark
            )
        }
        Text(
            text = state.weekAvgCostText
                ?.let { stringResource(R.string.week_avg_cost, it) }
                ?: stringResource(R.string.week_avg_cost_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
