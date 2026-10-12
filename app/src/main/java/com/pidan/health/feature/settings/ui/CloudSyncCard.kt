package com.pidan.health.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.DashboardCard
import com.pidan.health.core.designsystem.theme.BrandOrange
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.core.designsystem.theme.SubText
import com.pidan.health.feature.settings.viewmodel.CloudBackupItemUi
import com.pidan.health.feature.settings.viewmodel.CloudSyncViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 云端同步卡（T13）：WebDAV 配置 + 立即备份 + 云端备份列表恢复 + 自动备份开关。
 * 状态/错误内联展示（不占用页面级 Snackbar），恢复走防呆确认弹窗后覆盖导入。
 */
@Composable
fun CloudSyncCard(
    modifier: Modifier = Modifier,
    viewModel: CloudSyncViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_cloud_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_cloud_hint),
            style = MaterialTheme.typography.bodySmall,
            color = SubText
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.serverUrlText,
            onValueChange = viewModel::onServerUrlChange,
            label = { Text(text = stringResource(R.string.settings_cloud_server_url)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.usernameText,
            onValueChange = viewModel::onUsernameChange,
            label = { Text(text = stringResource(R.string.settings_cloud_username)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.passwordText,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(text = stringResource(R.string.settings_cloud_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            enabled = !state.isBusy,
            onClick = viewModel::testAndSaveConfig,
            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = androidx.compose.ui.graphics.Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = stringResource(R.string.settings_cloud_test))
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = BrandOrangeDark,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        ToggleRow(
            title = stringResource(R.string.settings_cloud_auto_backup),
            checked = state.autoBackupEnabled,
            onCheckedChange = viewModel::setAutoBackupEnabled
        )
        ToggleRow(
            title = stringResource(R.string.settings_cloud_wifi_only),
            checked = state.wifiOnly,
            onCheckedChange = viewModel::setWifiOnly
        )

        state.lastSyncText?.let { lastSync ->
            Text(
                text = stringResource(R.string.settings_cloud_last_sync, lastSync),
                style = MaterialTheme.typography.bodySmall,
                color = SubText,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = !state.isBusy,
            onClick = viewModel::backupNow,
            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.settings_cloud_backup_now))
        }
        OutlinedButton(
            enabled = !state.isBusy,
            onClick = viewModel::openBackups,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(text = stringResource(R.string.settings_cloud_view_backups), color = BrandOrangeDark)
        }
    }

    state.backups?.let { backups ->
        CloudBackupListDialog(
            backups = backups,
            isBusy = state.isBusy,
            onRestore = viewModel::onRestoreClick,
            onDismiss = viewModel::dismissBackups
        )
    }

    state.pendingRestoreFile?.let { fileName ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRestoreConfirm,
            title = { Text(text = stringResource(R.string.settings_cloud_restore_confirm_title)) },
            text = {
                Text(text = stringResource(R.string.settings_cloud_restore_confirm_text, fileName))
            },
            confirmButton = {
                Button(
                    enabled = !state.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                    onClick = viewModel::confirmRestore
                ) {
                    Text(text = stringResource(R.string.settings_cloud_restore))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isBusy,
                    onClick = viewModel::dismissRestoreConfirm
                ) {
                    Text(text = stringResource(R.string.settings_import_cancel))
                }
            }
        )
    }
}

@Composable
private fun CloudBackupListDialog(
    backups: List<CloudBackupItemUi>,
    isBusy: Boolean,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_cloud_view_backups)) },
        text = {
            if (backups.isEmpty()) {
                Text(text = stringResource(R.string.settings_cloud_backups_empty))
            } else {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(backups, key = { it.fileName }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${item.timeText} · ${item.sizeText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SubText
                                )
                            }
                            TextButton(
                                enabled = !isBusy,
                                onClick = { onRestore(item.fileName) }
                            ) {
                                Text(text = stringResource(R.string.settings_cloud_restore))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_import_cancel))
            }
        }
    )
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
