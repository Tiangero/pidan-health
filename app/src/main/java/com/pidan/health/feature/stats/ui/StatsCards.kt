package com.pidan.health.feature.stats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.pidan.health.core.data.model.FoodRankItem
import com.pidan.health.core.designsystem.component.DashboardCard
import com.pidan.health.core.designsystem.component.NutritionStatusColors
import com.pidan.health.core.designsystem.theme.BrandOrange
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.core.designsystem.theme.InkText
import com.pidan.health.core.designsystem.theme.RingTrack
import com.pidan.health.core.designsystem.theme.SubText
import com.pidan.health.core.domain.NutrientDeviation
import com.pidan.health.core.domain.NutritionDeviationSummary
import com.pidan.health.core.designsystem.component.statusColor
import com.pidan.health.feature.stats.viewmodel.BestProteinSource
import com.pidan.health.feature.stats.viewmodel.CostTrendStats
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 统计页三卡（对齐设计稿 s-stats）：
 * 1. 营养达标情况：均值摄入 vs 目标区间的 bar-row（M3 无「进度条 + 区间刻度线」现成组件，按设计稿自绘）
 * 2. 开销趋势：Vico 折线（带坐标轴）+ 最高/最低/累计标注
 * 3. 高频食物 Top5：次数 + 花费 + 性价比参考
 */

// ---------- 1. 营养达标情况 ----------

@Composable
fun NutritionDeviationCard(deviation: NutritionDeviationSummary?, modifier: Modifier = Modifier) {
    DashboardCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "营养达标情况",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.size(8.dp))
            StatTag(text = "均值 vs 目标")
        }
        Spacer(modifier = Modifier.height(14.dp))

        if (deviation == null) {
            EmptyHint(text = "区间内暂无饮食记录或目标快照")
        } else {
            DeviationBarRow(label = "碳水", item = deviation.carbs)
            DeviationBarRow(label = "蛋白质", item = deviation.protein)
            DeviationBarRow(label = "脂肪", item = deviation.fat)
            DeviationBarRow(label = "纤维", item = deviation.fiber)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "黑线为达标区间（碳水/脂肪 = 目标 ±5g 绝对容差，蛋白质/纤维 = 严格区间），柱色即达标状态。",
                style = MaterialTheme.typography.bodySmall,
                color = SubText
            )
        }
    }
}

@Composable
private fun DeviationBarRow(label: String, item: NutrientDeviation) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SubText,
            modifier = Modifier.width(52.dp)
        )
        DeviationBar(
            intakeGrams = item.avgIntakeGrams,
            targetMinGrams = item.targetMinGrams,
            targetMaxGrams = item.targetMaxGrams,
            color = item.status.statusColor(),
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
        )
        Text(
            text = formatDeviationValue(item),
            style = MaterialTheme.typography.bodySmall,
            color = InkText,
            modifier = Modifier.width(96.dp),
            textAlign = TextAlign.End
        )
    }
}

/** 柱 = 平均摄入占比；两条黑竖线 = 达标区间边界（同一比例尺） */
@Composable
private fun DeviationBar(
    intakeGrams: Float,
    targetMinGrams: Float,
    targetMaxGrams: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val scaleMax = maxOf(intakeGrams, targetMaxGrams).coerceAtLeast(0.1f) * 1.06f
    Canvas(modifier = modifier) {
        val trackHeight = 8.dp.toPx()
        val trackY = (size.height - trackHeight) / 2f
        val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

        drawRoundRect(
            color = RingTrack,
            topLeft = Offset(0f, trackY),
            size = Size(size.width, trackHeight),
            cornerRadius = corner
        )

        val fillFraction = (intakeGrams / scaleMax).coerceIn(0f, 1f)
        if (fillFraction > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, trackY),
                size = Size(size.width * fillFraction, trackHeight),
                cornerRadius = corner
            )
        }

        val markerWidth = 2.dp.toPx()
        val markerHeight = 16.dp.toPx()
        val markerY = (size.height - markerHeight) / 2f
        listOf(targetMinGrams, targetMaxGrams).forEach { target ->
            val x = size.width * (target / scaleMax).coerceIn(0f, 1f)
            drawRect(
                color = InkText,
                topLeft = Offset(x - markerWidth / 2f, markerY),
                size = Size(markerWidth, markerHeight)
            )
        }
    }
}

private fun formatDeviationValue(item: NutrientDeviation): String {
    val intake = item.avgIntakeGrams.roundToInt()
    val min = item.targetMinGrams.roundToInt()
    val max = item.targetMaxGrams.roundToInt()
    return "$intake / $min–$max g"
}

// ---------- 2. 开销趋势 ----------

@Composable
fun CostTrendCard(
    title: String,
    trend: CostTrendStats?,
    modifier: Modifier = Modifier
) {
    DashboardCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (trend == null || trend.points.isEmpty()) {
            EmptyHint(text = "区间内暂无饮食开销记录")
            return@DashboardCard
        }

        CostTrendChart(trend = trend)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtremumChip(
                text = "最高 ${formatYuan(trend.maxPoint?.cost)}",
                container = NutritionStatusColors.OverContainer,
                content = NutritionStatusColors.Over
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "累计 ${formatYuan(trend.totalCost)}",
                style = MaterialTheme.typography.labelLarge,
                color = BrandOrangeDark,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            ExtremumChip(
                text = "最低 ${formatYuan(trend.minPoint?.cost)}",
                container = NutritionStatusColors.OkContainer,
                content = NutritionStatusColors.Ok
            )
        }
    }
}

/** x 轴序数：用「日」（day-of-month）作为 x 值，缺记录的日期自然留空 */
private fun dayNumberOf(date: String): Int = LocalDate.parse(date).dayOfMonth

/** Vico 2.0 折线图（带坐标轴）：x = day-of-month，y = 当日花费 */
@Composable
private fun CostTrendChart(trend: CostTrendStats, modifier: Modifier = Modifier) {
    val model = remember(trend) {
        CartesianChartModel(
            LineCartesianLayerModel.build {
                series(
                    trend.points.map { dayNumberOf(it.date) },
                    trend.points.map { it.cost.toDouble() }
                )
            }
        )
    }
    val maxY = (trend.points.maxOf { it.cost } * 1.15f).coerceAtLeast(1f)
    val dayLabels = remember(trend) {
        trend.points.associate { dayNumberOf(it.date) to it.dayLabel }
    }

    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(BrandOrange)),
                    areaFill = LineCartesianLayer.AreaFill.single(
                        fill(NutritionStatusColors.UnderContainer)
                    )
                )
            ),
            rangeProvider = remember(maxY) {
                CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = maxY.toDouble())
            }
        ),
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom(
            valueFormatter = CartesianValueFormatter { _, value, _ ->
                dayLabels[value.roundToInt()] ?: value.roundToInt().toString()
            }
        )
    )

    CartesianChartHost(
        chart = chart,
        model = model,
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
    )
}

@Composable
private fun ExtremumChip(text: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content
        )
    }
}

// ---------- 3. 高频食物 Top5 ----------

@Composable
fun FoodRankCard(
    rangeLabel: String,
    topFoods: List<FoodRankItem>,
    bestProteinSource: BestProteinSource?,
    modifier: Modifier = Modifier
) {
    DashboardCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "高频食物 Top 5",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.size(8.dp))
            StatTag(text = rangeLabel)
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (topFoods.isEmpty()) {
            EmptyHint(text = "区间内暂无饮食记录")
        } else {
            val maxCount = topFoods.maxOf { it.eatCount }.coerceAtLeast(1)
            topFoods.forEachIndexed { index, item ->
                RankRow(
                    rank = index + 1,
                    item = item,
                    countFraction = item.eatCount / maxCount.toFloat()
                )
            }
            bestProteinSource?.let { best ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "性价比参考：${best.foodName} 约 " +
                        "%.1f".format(best.proteinPerYuan) + "g 蛋白质/元，为当前最优蛋白来源。",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubText
                )
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, item: FoodRankItem, countFraction: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = if (rank <= 3) BrandOrange else RingTrack,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelSmall,
                color = if (rank <= 3) Color.White else SubText,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = item.foodName,
            style = MaterialTheme.typography.bodyMedium,
            color = InkText,
            maxLines = 1,
            modifier = Modifier.width(76.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = "${item.eatCount} 次",
                style = MaterialTheme.typography.labelSmall,
                color = SubText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(RingTrack, RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(countFraction.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(BrandOrange, RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = "¥" + "%.1f".format(item.totalCost),
            style = MaterialTheme.typography.bodySmall,
            color = InkText
        )
    }
}

// ---------- 公共小件 ----------

@Composable
private fun StatTag(text: String) {
    Box(
        modifier = Modifier
            .background(RingTrack, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = SubText)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = SubText)
    }
}

private fun formatYuan(value: Float?): String =
    if (value == null) "--" else "¥" + "%.1f".format(value)
