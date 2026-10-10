package com.pidan.health.feature.stats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.AppTopBar
import com.pidan.health.core.designsystem.component.AppTopBarTitle
import com.pidan.health.core.designsystem.component.SegmentedToggle

/**
 * 统计分析页（骨架），三个统计模块在 T10 实现。
 * 顶栏对齐设计稿 s-stats：标题「统计分析」+ 右侧「本周/本月」分段器（T10 接数据）。
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    var selectedRange by remember { mutableIntStateOf(0) }

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
                        selectedIndex = selectedRange,
                        onSelect = { selectedRange = it },
                        modifier = Modifier
                            .width(130.dp)
                            .height(32.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(R.string.tab_stats))
        }
    }
}
