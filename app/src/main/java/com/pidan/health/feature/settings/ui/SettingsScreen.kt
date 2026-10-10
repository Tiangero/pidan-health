package com.pidan.health.feature.settings.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pidan.health.BuildConfig
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.AppTopBar
import com.pidan.health.core.designsystem.component.AppTopBarTitle
import com.pidan.health.core.designsystem.component.DashboardCard
import com.pidan.health.core.designsystem.theme.BrandOrange
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.core.designsystem.theme.SubText
import com.pidan.health.feature.settings.viewmodel.SettingsUiState
import com.pidan.health.feature.settings.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 设置页（T11）：备份与恢复（导出/导入 zip）+ 自定义营养系数完整配置 + 版本信息。
 * 导入走系统文件选择器，确认弹窗防呆后覆盖导入；导出落 Downloads/皮蛋健康/。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onZipPicked(uri)
    }

    // 旧版本（API < 29）写公开 Downloads 需要存储权限；29+ 走 MediaStore 免权限
    val legacyStoragePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.export() else viewModel.onLegacyStorageDenied()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(data) } },
        topBar = {
            AppTopBar(
                title = { AppTopBarTitle(stringResource(R.string.topbar_title_settings)) },
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "backup") {
                BackupCard(
                    isBusy = state.isBusy,
                    onExport = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            viewModel.export()
                        } else {
                            legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    },
                    onImport = { zipPicker.launch(arrayOf("application/zip")) }
                )
            }
            item(key = "factors") {
                FactorConfigCard(
                    state = state,
                    onCarbsChange = viewModel::onCarbsFactorChange,
                    onProteinMinChange = viewModel::onProteinMinChange,
                    onProteinMaxChange = viewModel::onProteinMaxChange,
                    onFatChange = viewModel::onFatFactorChange,
                    onFiberCustomChange = viewModel::onFiberCustomChange,
                    onFiberMinChange = viewModel::onFiberMinChange,
                    onFiberMaxChange = viewModel::onFiberMaxChange,
                    onSaltCustomChange = viewModel::onSaltCustomChange,
                    onSaltMaxChange = viewModel::onSaltMaxChange,
                    onSave = viewModel::saveFactors
                )
            }
            item(key = "version") {
                DashboardCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_version_label),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "皮蛋健康 v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SubText
                        )
                    }
                }
            }
        }
    }

    if (state.isImportConfirmVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissImportConfirm,
            title = { Text(text = stringResource(R.string.settings_import_confirm_title)) },
            text = {
                Text(text = stringResource(R.string.settings_import_confirm_text))
            },
            confirmButton = {
                Button(
                    enabled = !state.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                    onClick = viewModel::confirmImport
                ) {
                    Text(text = stringResource(R.string.settings_import_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isBusy,
                    onClick = viewModel::dismissImportConfirm
                ) {
                    Text(text = stringResource(R.string.settings_import_cancel))
                }
            }
        )
    }
}

@Composable
private fun BackupCard(
    isBusy: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    DashboardCard {
        Text(
            text = stringResource(R.string.settings_backup_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            enabled = !isBusy,
            onClick = onExport,
            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = stringResource(R.string.settings_export))
        }
        Text(
            text = stringResource(R.string.settings_export_hint),
            style = MaterialTheme.typography.bodySmall,
            color = SubText,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        OutlinedButton(
            enabled = !isBusy,
            onClick = onImport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.settings_import),
                color = BrandOrangeDark
            )
        }
        Text(
            text = stringResource(R.string.settings_import_hint),
            style = MaterialTheme.typography.bodySmall,
            color = SubText,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun FactorConfigCard(
    state: SettingsUiState,
    onCarbsChange: (String) -> Unit,
    onProteinMinChange: (String) -> Unit,
    onProteinMaxChange: (String) -> Unit,
    onFatChange: (String) -> Unit,
    onFiberCustomChange: (Boolean) -> Unit,
    onFiberMinChange: (String) -> Unit,
    onFiberMaxChange: (String) -> Unit,
    onSaltCustomChange: (Boolean) -> Unit,
    onSaltMaxChange: (String) -> Unit,
    onSave: () -> Unit
) {
    DashboardCard {
        Text(
            text = stringResource(R.string.settings_factors_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_factors_hint),
            style = MaterialTheme.typography.bodySmall,
            color = SubText
        )
        Spacer(modifier = Modifier.height(12.dp))

        FactorInputRow(
            labelRes = R.string.settings_factor_carbs,
            value = state.carbsFactorText,
            onValueChange = onCarbsChange
        )
        FactorInputRow(
            labelRes = R.string.settings_factor_protein_min,
            value = state.proteinMinText,
            onValueChange = onProteinMinChange
        )
        FactorInputRow(
            labelRes = R.string.settings_factor_protein_max,
            value = state.proteinMaxText,
            onValueChange = onProteinMaxChange
        )
        FactorInputRow(
            labelRes = R.string.settings_factor_fat,
            value = state.fatFactorText,
            onValueChange = onFatChange
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        CustomToggleRow(
            title = stringResource(R.string.settings_fiber_custom),
            checked = state.isFiberCustom,
            onCheckedChange = onFiberCustomChange
        )
        if (state.isFiberCustom) {
            FactorInputRow(
                labelRes = R.string.settings_factor_fiber_min,
                value = state.fiberMinText,
                onValueChange = onFiberMinChange
            )
            FactorInputRow(
                labelRes = R.string.settings_factor_fiber_max,
                value = state.fiberMaxText,
                onValueChange = onFiberMaxChange
            )
        }

        CustomToggleRow(
            title = stringResource(R.string.settings_salt_custom),
            checked = state.isSaltCustom,
            onCheckedChange = onSaltCustomChange
        )
        if (state.isSaltCustom) {
            FactorInputRow(
                labelRes = R.string.settings_factor_salt_max,
                value = state.saltMaxText,
                onValueChange = onSaltMaxChange
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.settings_factors_save))
        }
    }
}

@Composable
private fun FactorInputRow(labelRes: Int, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = stringResource(labelRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CustomToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = BrandOrange)
        )
    }
}
