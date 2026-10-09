package com.pidan.health.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.ui.graphics.vector.ImageVector
import com.pidan.health.R

/**
 * 底部四个一级 Tab 的路由定义。
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    RECORD("record", R.string.tab_record, Icons.Rounded.Home),
    FOOD("food", R.string.tab_food, Icons.AutoMirrored.Rounded.MenuBook),
    EXERCISE("exercise", R.string.tab_exercise, Icons.AutoMirrored.Rounded.DirectionsRun),
    STATS("stats", R.string.tab_stats, Icons.Rounded.Insights);

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}
