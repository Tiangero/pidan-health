package com.pidan.health.feature.exercise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pidan.health.R
import com.pidan.health.core.designsystem.theme.BrandOrange
import com.pidan.health.core.designsystem.theme.BrandOrangeContainer
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.feature.exercise.viewmodel.ExerciseType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 运动状态配色（对齐设计稿 exColor）：有氧红 / 无氧蓝 / 休息灰。
 */
object ExerciseStatusColors {
    val Aerobic = Color(0xFFEF4444)
    val Anaerobic = Color(0xFF3B82F6)
    val Rest = Color(0xFFD6D3D1)

    fun of(type: ExerciseType): Color = when (type) {
        ExerciseType.AEROBIC -> Aerobic
        ExerciseType.ANAEROBIC -> Anaerobic
        ExerciseType.REST -> Rest
    }
}

/**
 * 「设置运动状态」底部弹层（对齐设计稿 .sheet / .ex-opt）：
 * 三个状态选项行（圆点 + 文案），当前状态行高亮（品牌描边 + 浅橙底）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseStatusSheet(
    date: LocalDate,
    currentType: ExerciseType,
    onPick: (ExerciseType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.exercise_sheet_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = date.format(DateTimeFormatter.ofPattern("M月d日")),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )
            ExerciseType.entries.forEach { type ->
                StatusOptionRow(
                    type = type,
                    selected = type == currentType,
                    onClick = { onPick(type) }
                )
            }
        }
    }
}

@Composable
private fun StatusOptionRow(
    type: ExerciseType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(
                color = if (selected) BrandOrangeContainer else MaterialTheme.colorScheme.surface,
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (selected) BrandOrange else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = ExerciseStatusColors.of(type), shape = CircleShape)
        )
        Text(
            text = stringResource(type.labelRes),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) BrandOrangeDark else MaterialTheme.colorScheme.onSurface
        )
    }
}

val ExerciseType.labelRes: Int
    get() = when (this) {
        ExerciseType.AEROBIC -> R.string.exercise_type_aerobic
        ExerciseType.ANAEROBIC -> R.string.exercise_type_anaerobic
        ExerciseType.REST -> R.string.exercise_type_rest
    }
