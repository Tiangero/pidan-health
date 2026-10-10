package com.pidan.health.feature.record.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pidan.health.core.data.model.WeightTrendItem
import com.pidan.health.core.designsystem.component.NutritionStatusColors
import com.pidan.health.core.designsystem.theme.BrandOrange

/**
 * 体重迷你折线图（sparkline，无轴，自绘 Canvas 对齐设计稿 #sparkline）：
 * - 单个点：只画一个高亮圆点（今天刚记录、无历史）；
 * - 多个点：浅橙蒙层（#FFF3E8）+ 橙色折线 + 末端点高亮（橙底白描边）；
 * - x 轴为点序号（不承载日期语义），缺失日期的记录点直接跳过连线；
 * - y 范围取数据 min/max ±0.15kg，避免 0 基线把小幅波动压成横线。
 */
@Composable
fun WeightTrendChart(
    weights: List<WeightTrendItem>,
    modifier: Modifier = Modifier
) {
    if (weights.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        val pad = 6.dp.toPx()
        val last = weights.last().weight

        // 单点：居中高亮圆点，无连线无蒙层
        if (weights.size == 1) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = BrandOrange, radius = 4.dp.toPx(), center = center)
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            return@Canvas
        }

        val minW = weights.minOf { it.weight } - 0.15f
        val maxW = weights.maxOf { it.weight } + 0.15f
        val spanY = (maxW - minW).coerceAtLeast(0.001f)
        val stepX = (size.width - 2 * pad) / (weights.size - 1)

        fun px(i: Int) = pad + i * stepX
        fun py(v: Float) = pad + (maxW - v) / spanY * (size.height - 2 * pad)

        // 折线各点坐标
        val points = weights.mapIndexed { i, item -> Offset(px(i), py(item.weight)) }

        // 浅橙蒙层：折线以下、贴到图表底部（设计稿 polygon 首尾接 h-pad）
        val areaPath = Path().apply {
            moveTo(points.first().x, size.height - pad)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height - pad)
            close()
        }
        drawPath(areaPath, color = NutritionStatusColors.UnderContainer)

        // 橙色折线
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = linePath,
            color = BrandOrange,
            style = Stroke(
                width = 2.dp.toPx(),
                join = StrokeJoin.Round,
                cap = StrokeCap.Round
            )
        )

        // 末端点高亮：橙底 + 白描边
        val end = Offset(px(weights.size - 1), py(last))
        drawCircle(color = BrandOrange, radius = 3.5.dp.toPx(), center = end)
        drawCircle(
            color = Color.White,
            radius = 3.5.dp.toPx(),
            center = end,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}
