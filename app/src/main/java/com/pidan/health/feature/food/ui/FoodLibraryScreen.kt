package com.pidan.health.feature.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.AppTopBar
import com.pidan.health.core.designsystem.component.AppTopBarTitle
import com.pidan.health.core.designsystem.theme.BrandOrangeDark

/**
 * 食物库页（骨架），列表与快捷录入在 T5 实现。
 * 顶栏对齐设计稿 s-foods：标题「食物库」+ 右侧「管理」。
 */
@Composable
fun FoodLibraryScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = { AppTopBarTitle(stringResource(R.string.topbar_title_food)) },
                actions = {
                    TextButton(onClick = { /* TODO(T5): 进入管理模式（批量删除） */ }) {
                        Text(
                            text = stringResource(R.string.topbar_action_manage),
                            style = MaterialTheme.typography.labelLarge,
                            color = BrandOrangeDark
                        )
                    }
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
            Text(text = stringResource(R.string.topbar_title_food))
        }
    }
}
