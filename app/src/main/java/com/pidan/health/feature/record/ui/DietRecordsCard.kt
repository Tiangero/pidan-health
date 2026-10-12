package com.pidan.health.feature.record.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.DashboardCard
import com.pidan.health.feature.record.viewmodel.DietRecordItemUi

/**
 * 今日饮食明细卡片：逐条列出当天吃了什么、吃了多少（克/份），
 * 点击条目展开查看本条固化摄入的五大营养 g 数与花费。
 * 展开状态为纯 UI 局部状态，不进 VM。
 */
@Composable
fun DietRecordsCard(
    records: List<DietRecordItemUi>,
    modifier: Modifier = Modifier
) {
    var expandedId by remember { mutableStateOf<Long?>(null) }

    DashboardCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.record_diet_title),
            style = MaterialTheme.typography.titleMedium
        )

        if (records.isEmpty()) {
            Text(
                text = stringResource(R.string.diet_records_empty),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@DashboardCard
        }

        records.forEachIndexed { index, record ->
            DietRecordRow(
                record = record,
                expanded = expandedId == record.id,
                onClick = {
                    expandedId = if (expandedId == record.id) null else record.id
                },
                modifier = Modifier.padding(top = 12.dp)
            )
            if (index < records.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun DietRecordRow(
    record: DietRecordItemUi,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = record.foodName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = record.amountText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起详情" else "展开详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }

        if (expanded) {
            RecordNutrientDetail(record = record)
        }
    }
}

/** 展开区：五大营养等宽五列（名称在上、数值在下），有花费时右下角补充展示 */
@Composable
private fun RecordNutrientDetail(
    record: DietRecordItemUi,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            record.nutrientValues.forEach { nutrient ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(nutrient.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = nutrient.valueText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        if (record.costText != null) {
            Text(
                text = stringResource(R.string.diet_record_cost, record.costText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
            )
        }
    }
}
