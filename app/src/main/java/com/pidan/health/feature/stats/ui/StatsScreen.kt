package com.pidan.health.feature.stats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.AppTopBar
import com.pidan.health.core.designsystem.component.AppTopBarTitle
import com.pidan.health.core.designsystem.component.SegmentedToggle
import com.pidan.health.feature.stats.viewmodel.StatsRange
import com.pidan.health.feature.stats.viewmodel.StatsViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 统计分析页（T10）：营养达标情况 + 开销趋势 + 高频食物 Top5。
 * 顶栏右侧「本周/本月」分段器切换统计维度，回到前台自动重算。
 */
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = { AppTopBarTitle(stringResource(R.string.topbar_title_stats)) },
                actions = {
                    SegmentedToggle(
                        options = listOf(
                            stringResource(R.string.stats_range_week),
                            stringResource(R.string.stats_range_month)
                        ),
                        selectedIndex = when (state.range) {
                            StatsRange.WEEK -> 0
                            StatsRange.MONTH -> 1
                        },
                        onSelect = { index ->
                            viewModel.setRange(
                                if (index == 0) StatsRange.WEEK else StatsRange.MONTH
                            )
                        },
                        modifier = Modifier
                            .width(130.dp)
                            .height(32.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "deviation") {
                NutritionDeviationCard(deviation = state.deviation)
            }
            item(key = "cost") {
                CostTrendCard(
                    title = "${state.rangeLabel}开销趋势",
                    trend = state.costTrend
                )
            }
            item(key = "rank") {
                FoodRankCard(
                    rangeLabel = state.rangeLabel,
                    topFoods = state.topFoods,
                    bestProteinSource = state.bestProteinSource
                )
            }
        }
    }
}
