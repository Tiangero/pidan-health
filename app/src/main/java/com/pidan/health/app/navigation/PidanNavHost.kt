package com.pidan.health.app.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pidan.health.feature.exercise.ui.ExerciseScreen
import com.pidan.health.feature.food.ui.FoodDetailScreen
import com.pidan.health.feature.food.ui.FoodEditScreen
import com.pidan.health.feature.food.ui.FoodLibraryScreen
import com.pidan.health.feature.record.ui.RecordScreen
import com.pidan.health.feature.settings.ui.SettingsScreen
import com.pidan.health.feature.splash.ui.SplashScreen
import com.pidan.health.feature.stats.ui.StatsScreen

/** 一级 Tab 之外的路由（二级页） */
object Routes {
    /** 新建/编辑食物页：foodId 缺省 -1 表示新建，新建与编辑复用同一页面 */
    const val FOOD_EDIT = "food_edit?foodId={foodId}"
    const val FOOD_EDIT_ARG_FOOD_ID = "foodId"
    fun foodEdit(foodId: Long? = null): String =
        if (foodId != null) "food_edit?foodId=$foodId" else "food_edit"

    /** 食物详情页（只读）：与编辑页分离，顶栏「编辑」再进编辑页 */
    const val FOOD_DETAIL = "food_detail?foodId={foodId}"
    fun foodDetail(foodId: Long): String = "food_detail?foodId=$foodId"

    /** 设置页（T11：备份导出/导入 + 自定义营养系数） */
    const val SETTINGS = "settings"
}

/** 开屏遮罩停留时长：遮罩下首页早已组装好并预热完数据，到点整体淡出 */
private const val SPLASH_STAY_MS = 1_600L

/** 开屏遮罩淡出时长 */
private const val SPLASH_FADE_MS = 350

/**
 * 应用导航宿主：单 Activity + 底部四 Tab。
 *
 * 开屏页不再作为导航路由，而是一个盖在整个 Scaffold（含底部导航栏）之上的
 * 全屏遮罩：首页从首帧起就在遮罩下组装并加载数据（loading 不会暴露给用户），
 * 停留结束后遮罩整体淡出，首页与底部 Tab 一起露出——
 * 修复了旧实现「导航瞬间 Tab 先冒出来、开屏页还在淡出」的割裂感。
 */
@Composable
fun PidanNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = TopLevelDestination.fromRoute(backStackEntry?.destination?.route)

    var splashVisible by remember { mutableStateOf(true) }
    val splashAlpha by animateFloatAsState(
        targetValue = if (splashVisible) 1f else 0f,
        animationSpec = tween(durationMillis = SPLASH_FADE_MS),
        label = "splashOverlayAlpha"
    )

    // 停留结束后开始淡出（数据预热在遮罩下同步进行）
    LaunchedEffect(Unit) {
        delay(SPLASH_STAY_MS)
        splashVisible = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // 顶部 inset 不在这里消费：状态栏底色由各页顶栏（AppTopBar）自绘白色并自行
            // statusBarsPadding，否则外层会把 NavHost 推到状态栏下方，露出米色底
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // 二级页（不在 TopLevelDestination 中）不显示底部 Tab
                if (currentDestination != null) {
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
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.RECORD.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(TopLevelDestination.RECORD.route) {
                    RecordScreen(
                        // 首开体重引导弹窗等遮罩退场后再弹，避免压在开屏页上
                        showGuide = !splashVisible,
                        onNavigateToFood = {
                            navController.navigate(TopLevelDestination.FOOD.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }
                composable(TopLevelDestination.FOOD.route) {
                    FoodLibraryScreen(
                        onNavigateToFoodDetail = { foodId ->
                            navController.navigate(Routes.foodDetail(foodId))
                        },
                        onNavigateToFoodEdit = { foodId -> navController.navigate(Routes.foodEdit(foodId)) }
                    )
                }
                composable(
                    route = Routes.FOOD_DETAIL,
                    arguments = listOf(
                        navArgument(Routes.FOOD_EDIT_ARG_FOOD_ID) {
                            type = NavType.LongType
                            defaultValue = -1L
                        }
                    )
                ) { entry ->
                    val foodId = entry.arguments?.getLong(Routes.FOOD_EDIT_ARG_FOOD_ID) ?: -1L
                    FoodDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(Routes.foodEdit(foodId)) }
                    )
                }
                composable(
                    route = Routes.FOOD_EDIT,
                    arguments = listOf(
                        navArgument(Routes.FOOD_EDIT_ARG_FOOD_ID) {
                            type = NavType.LongType
                            defaultValue = -1L
                        }
                    )
                ) {
                    FoodEditScreen(onBack = { navController.popBackStack() })
                }
                composable(TopLevelDestination.EXERCISE.route) {
                    ExerciseScreen()
                }
                composable(TopLevelDestination.STATS.route) {
                    StatsScreen()
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        // 开屏遮罩：置于最上层并拦截所有手势，防止误触底下的首页
        if (splashAlpha > 0f) {
            SplashScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(splashAlpha)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                if (event.changes.all { !it.pressed }) break
                            }
                        }
                    }
            )
        }
    }
}
