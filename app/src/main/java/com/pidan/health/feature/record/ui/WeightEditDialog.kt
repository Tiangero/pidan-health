package com.pidan.health.feature.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * 体重编辑弹窗（编辑 + 首开引导共用）。
 * 输入实时预览新目标（同一计算链路），确认后写入当天快照，历史快照不动。
 */
@Composable
fun WeightEditDialog(
    initialWeight: Float?,
    previewTargets: List<String>?,
    onWeightInputChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null
) {
    var weightText by remember { mutableStateOf(initialWeight?.toString() ?: "") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val parsed = weightText.toFloatOrNull()
    val isValid = parsed != null && parsed > 0f && parsed < 500f
    val showInvalid = weightText.isNotEmpty() && !isValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.weight_dialog_title)) },
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
                onClick = { onConfirm(weightText) }
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
}
