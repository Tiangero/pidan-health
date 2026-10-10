package com.pidan.health.feature.exercise.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.AppTopBar
import com.pidan.health.core.designsystem.component.AppTopBarTitle

/**
 * 运动页（骨架），月历打卡在 T9 实现。
 * 顶栏对齐设计稿 s-sport：标题「运动记录」+ 右侧月份（T9 接入动态月份）。
 */
@Composable
fun ExerciseScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = { AppTopBarTitle(stringResource(R.string.topbar_title_exercise)) },
                actions = {
                    Text(
                        text = "2026 年 10 月",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            Text(text = stringResource(R.string.tab_exercise))
        }
    }
}
