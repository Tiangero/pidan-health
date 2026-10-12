package com.pidan.health.feature.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.AppTopBar
import com.pidan.health.core.designsystem.component.BrandLogoTitle
import com.pidan.health.core.designsystem.theme.BrandOrange
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.feature.record.viewmodel.RecordViewModel
import com.pidan.health.feature.record.viewmodel.WeightChartRange
import org.koin.androidx.compose.koinViewModel

/**
 * 首页 · 记录页：体重卡片 + 营养卡片 + 开销卡片 + FAB（跳食物库）。
 * 数据只来自 diet_record 聚合 + daily_snapshot（红线），逻辑全在 RecordViewModel。
 */
@Composable
fun RecordScreen(
    /** 首开体重引导弹窗开关：开屏遮罩退场后才放行，避免弹窗压在开屏页上 */
    showGuide: Boolean = true,
    onNavigateToFood: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val previewTargets by viewModel.weightPreview.collectAsStateWithLifecycle()

    // 每次回到前台：跨天检测 + 重新订阅当天数据流
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    // 编辑弹窗 / 首开引导弹窗共用，一次只显示一个
    var showWeightDialog by remember { mutableStateOf(false) }
    val showSetupGuide = showGuide && state.needsWeightSetup && !showWeightDialog

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // 底部 inset 已由外层 PidanNavHost 的 NavigationBar 消费，这里再算一次会出现正文与导航栏之间的空隙
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = { BrandLogoTitle() },
                actions = {
                    Text(
                        text = state.todayLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onNavigateToSettings) {
                        Text(
                            text = stringResource(R.string.action_settings),
                            style = MaterialTheme.typography.labelLarge,
                            color = BrandOrangeDark
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToFood,
                shape = RoundedCornerShape(14.dp),
                containerColor = BrandOrange,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = "快捷录入")
            }
        }
    ) { innerPadding ->
        // 数据全部来自本地 Room，首次发射在一两帧内到达；
        // 首屏直接渲染内容（首帧还会被开屏遮罩盖住），不再显示全屏 loading
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "weight") {
                WeightCard(
                    state = state,
                    onEditWeightClick = { showWeightDialog = true },
                    onChartRangeChange = { viewModel.setChartRange(it) }
                )
            }
            item(key = "nutrition") {
                NutritionCard(state = state)
            }
            item(key = "cost") {
                CostCard(state = state)
            }
        }
    }

    if (showWeightDialog || showSetupGuide) {
        WeightEditDialog(
            initialWeight = if (showSetupGuide) null else state.todayWeight,
            previewTargets = previewTargets,
            onWeightInputChange = viewModel::onWeightDraftChanged,
            onConfirm = { weightText, date ->
                viewModel.confirmWeightInput(weightText, date)
                showWeightDialog = false
            },
            onDismiss = {
                viewModel.dismissWeightDialog()
                showWeightDialog = false
            }
        )
    }
}
