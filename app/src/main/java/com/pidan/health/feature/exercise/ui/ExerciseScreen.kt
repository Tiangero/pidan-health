package com.pidan.health.feature.exercise.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.AppTopBar
import com.pidan.health.core.designsystem.component.AppTopBarTitle
import com.pidan.health.core.designsystem.component.DashboardCard
import com.pidan.health.feature.exercise.viewmodel.ExerciseType
import com.pidan.health.feature.exercise.viewmodel.ExerciseViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 运动页：月历打卡 + 本月概览。
 * 打卡/补签写入 exercise_record（按主键 date 覆盖），未来日期禁用。
 */
@Composable
fun ExerciseScreen(
    modifier: Modifier = Modifier,
    viewModel: ExerciseViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = { AppTopBarTitle(stringResource(R.string.topbar_title_exercise)) },
                actions = {
                    IconButton(
                        onClick = { viewModel.selectMonth(state.displayedMonth.minusMonths(1)) }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.exercise_prev_month),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = state.monthLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { viewModel.selectMonth(state.displayedMonth.plusMonths(1)) }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.exercise_next_month),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "calendar") {
                DashboardCard {
                    ExerciseCalendar(
                        month = state.displayedMonth,
                        today = viewModel.today,
                        statusByDate = state.statusByDate,
                        onDayClick = viewModel::onDayClick
                    )
                }
            }
            item(key = "overview") {
                DashboardCard {
                    MonthOverviewCard(
                        aerobicDays = state.overview.aerobicDays,
                        anaerobicDays = state.overview.anaerobicDays,
                        restDays = state.overview.restDays
                    )
                }
            }
        }
    }

    state.pickerDate?.let { date ->
        ExerciseStatusSheet(
            date = date,
            currentType = state.statusByDate[date.toString()] ?: ExerciseType.REST,
            onPick = { type -> viewModel.onStatusPicked(date, type) },
            onDismiss = viewModel::dismissPicker
        )
    }
}

/** 本月概览卡片（对齐设计稿「本月概览」）：三类状态天数 + 底部提示 */
@Composable
private fun MonthOverviewCard(
    aerobicDays: Int,
    anaerobicDays: Int,
    restDays: Int
) {
    Column {
        Text(
            text = stringResource(R.string.exercise_overview_title),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.padding(top = 6.dp))
        OverviewRow(label = stringResource(ExerciseType.AEROBIC.labelRes), days = aerobicDays)
        OverviewRow(label = stringResource(ExerciseType.ANAEROBIC.labelRes), days = anaerobicDays)
        OverviewRow(label = stringResource(ExerciseType.REST.labelRes), days = restDays)
        Text(
            text = stringResource(R.string.exercise_overview_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun OverviewRow(label: String, days: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.exercise_days_count, days),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
