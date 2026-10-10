package com.pidan.health.feature.exercise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pidan.health.R
import com.pidan.health.core.designsystem.theme.BrandOrange
import com.pidan.health.core.designsystem.theme.BrandOrangeContainer
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.feature.exercise.viewmodel.ExerciseType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil

/**
 * 月历视图（对齐设计稿 .cal / .cal-head / .cal-grid / .legend）：
 * - 周一为一周起始，一屏整月；
 * - 每格 = 日期数字 + 状态圆点（无记录 = 休息灰点）；
 * - 今天高亮（浅橙底 + 橙描边）；未来日期禁用点击且降透明度。
 */
@Composable
fun ExerciseCalendar(
    month: YearMonth,
    today: LocalDate,
    statusByDate: Map<String, ExerciseType>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WeekHeadRow()
        Spacer(Modifier.height(8.dp))
        val leadingBlanks = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
        val cellCount = leadingBlanks + month.lengthOfMonth()
        repeat(ceil(cellCount / 7.0).toInt()) { rowIndex ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { colIndex ->
                    val index = rowIndex * 7 + colIndex
                    val day = index - leadingBlanks + 1
                    Box(modifier = Modifier.weight(1f)) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            CalendarDayCell(
                                date = date,
                                today = today,
                                status = statusByDate[date.toString()] ?: ExerciseType.REST,
                                enabled = !date.isAfter(today),
                                onClick = onDayClick
                            )
                        }
                    }
                }
            }
        }
        LegendRow(modifier = Modifier.padding(top = 14.dp))
    }
}

/** 表头：一 ~ 日（周一起始，对齐设计稿 .cal-head） */
@Composable
private fun WeekHeadRow() {
    val labels = listOf(
        R.string.exercise_week_mon, R.string.exercise_week_tue, R.string.exercise_week_wed,
        R.string.exercise_week_thu, R.string.exercise_week_fri, R.string.exercise_week_sat,
        R.string.exercise_week_sun
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { res ->
            Text(
                text = stringResource(res),
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    today: LocalDate,
    status: ExerciseType,
    enabled: Boolean,
    onClick: (LocalDate) -> Unit
) {
    val isToday = date == today
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
            .aspectRatio(1f)
            .alpha(if (enabled) 1f else 0.35f)
            .background(
                color = if (isToday) BrandOrangeContainer else Color.Transparent,
                shape = shape
            )
            .then(
                if (isToday) {
                    Modifier.border(width = 1.5.dp, color = BrandOrange, shape = shape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled) { onClick(date) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 13.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) BrandOrangeDark else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .height(6.dp)
                .aspectRatio(1f)
                .background(color = ExerciseStatusColors.of(status), shape = CircleShape)
        )
    }
}

/** 图例：有氧 / 无氧 / 休息（对齐设计稿 .legend） */
@Composable
private fun LegendRow(modifier: Modifier = Modifier) {
    val entries = listOf(
        ExerciseType.AEROBIC, ExerciseType.ANAEROBIC, ExerciseType.REST
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        entries.forEach { type ->
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .aspectRatio(1f)
                        .background(color = ExerciseStatusColors.of(type), shape = CircleShape)
                )
                Text(
                    text = stringResource(type.labelRes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
