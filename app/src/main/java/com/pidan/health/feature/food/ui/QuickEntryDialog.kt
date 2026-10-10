package com.pidan.health.feature.food.ui

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
import com.pidan.health.core.domain.FoodUnit
import com.pidan.health.feature.food.viewmodel.FoodItemUi

/**
 * 快捷录入数量输入弹窗：按模板单位输入份数或克数，
 * 按份时提示单份克重；确认后走 VM 固化计算落库。
 */
@Composable
fun QuickEntryDialog(
    item: FoodItemUi,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val amount = amountText.toFloatOrNull()
    val isValid = amount != null && amount > 0f
    val showInvalid = amountText.isNotEmpty() && !isValid
    val isByServing = item.unitType == FoodUnit.BY_SERVING

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.quick_entry_title, item.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = {
                        Text(
                            text = stringResource(
                                if (isByServing) R.string.quick_entry_amount_by_serving
                                else R.string.quick_entry_amount_by_gram
                            )
                        )
                    },
                    isError = showInvalid,
                    supportingText = {
                        if (showInvalid) {
                            Text(text = stringResource(R.string.quick_entry_invalid))
                        } else if (isByServing && item.weightPerServing != null) {
                            Text(
                                text = stringResource(
                                    R.string.quick_entry_serving_hint,
                                    formatServingGrams(item.weightPerServing)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(amountText) }
            ) {
                Text(text = stringResource(R.string.quick_entry_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.quick_entry_cancel))
            }
        }
    )
}

private fun formatServingGrams(grams: Float): String =
    if (grams == grams.toInt().toFloat()) "${grams.toInt()}g" else "${grams}g"
