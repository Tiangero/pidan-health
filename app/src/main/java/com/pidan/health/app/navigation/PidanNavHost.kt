package com.pidan.health.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pidan.health.feature.exercise.ui.ExerciseScreen
import com.pidan.health.feature.food.ui.FoodLibraryScreen
import com.pidan.health.feature.record.ui.RecordScreen
import com.pidan.health.feature.stats.ui.StatsScreen

/**
 * 应用导航宿主：单 Activity + 底部四 Tab。
 */
@Composable
fun PidanNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = TopLevelDestination.fromRoute(backStackEntry?.destination?.route)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // 顶部 inset 不在这里消费：状态栏底色由各页顶栏（AppTopBar）自绘白色并自行
        // statusBarsPadding，否则外层会把 NavHost 推到状态栏下方，露出米色底
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                // 设计稿 .tabbar：白底 + 顶部 1px 分隔线；containerColor 显式压成 surface，
                // 避免 M3 默认 surfaceContainer 的紫灰色系漏出
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination == destination,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(imageVector = destination.icon, contentDescription = null)
                            },
                            label = { Text(text = stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.RECORD.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopLevelDestination.RECORD.route) {
                RecordScreen(
                    onNavigateToFood = {
                        navController.navigate(TopLevelDestination.FOOD.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(TopLevelDestination.FOOD.route) {
                FoodLibraryScreen()
            }
            composable(TopLevelDestination.EXERCISE.route) {
                ExerciseScreen()
            }
            composable(TopLevelDestination.STATS.route) {
                StatsScreen()
            }
        }
    }
}
