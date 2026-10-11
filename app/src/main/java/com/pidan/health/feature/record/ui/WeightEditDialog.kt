package com.pidan.health.feature.record.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pidan.health.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 体重编辑弹窗（编辑 + 首开引导共用）。
 * 输入实时预览新目标（同一计算链路），确认后写入快照，其余历史快照不动。
 * 记录日期支持补录历史数据（2026-10-10）：默认今天，可切换到过去任一天；
 * 不允许选未来日期（选择器禁用 + VM 提交时二次校验）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightEditDialog(
    initialWeight: Float?,
    previewTargets: List<String>?,
    onWeightInputChange: (String) -> Unit,
    onConfirm: (weightText: String, date: String?) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null
) {
    var weightText by remember { mutableStateOf(initialWeight?.toString() ?: "") }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val parsed = weightText.toFloatOrNull()
    val isValid = parsed != null && parsed > 0f && parsed < 500f
    val showInvalid = weightText.isNotEmpty() && !isValid

    // 日期选择状态：只允许今天及以前（UTC 毫秒口径与 M3 DatePicker 对齐）
    val today: LocalDate = LocalDate.now()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneOffset.UTC).toLocalDate()
                return !date.isAfter(today)
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = selectedDate?.let {
                    stringResource(R.string.weight_dialog_title_backfill, formatDateLabel(it))
                } ?: stringResource(R.string.weight_dialog_title)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = {
                        weightText = it
                        onWeightInputChange(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text(text = stringResource(R.string.weight_dialog_hint)) },
                    isError = showInvalid,
                    supportingText = {
                        if (showInvalid) {
                            Text(text = stringResource(R.string.weight_dialog_invalid))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                // 记录日期（只读字段 + 点击弹官方 DatePicker）。readOnly 字段内部的
                // 文本手势会先消费点击，外层 clickable/interactionSource 共享收不到事件，
                // 所以改用一层透明浮层（matchParentSize）覆盖整个字段区域接管点击。
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedDate?.let(::formatDateLabel)
                            ?: stringResource(R.string.weight_dialog_date_today),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(text = stringResource(R.string.weight_dialog_date_label)) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = stringResource(R.string.weight_dialog_date_pick)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showDatePicker = true }
                    )
                }

                if (previewTargets != null) {
                    Text(
                        text = stringResource(R.string.weight_dialog_preview_title),
                        style = MaterialTheme.typography.labelLarge
                    )
                    previewTargets.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (onNavigateToSettings != null) {
                    TextButton(
                        onClick = onNavigateToSettings,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.weight_dialog_factors_link),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(weightText, selectedDate?.toString()) }
            ) {
                Text(text = stringResource(R.string.weight_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.weight_dialog_cancel))
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(text = stringResource(R.string.date_picker_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(R.string.weight_dialog_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** 「2026-10-08」→「10月8日」（跨年补录时带年份） */
private fun formatDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return if (date.year == today.year) {
        "${date.monthValue}月${date.dayOfMonth}日"
    } else {
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
}
