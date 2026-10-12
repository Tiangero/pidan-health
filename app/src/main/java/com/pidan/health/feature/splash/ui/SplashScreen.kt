package com.pidan.health.feature.splash.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.pidan.health.R
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.core.designsystem.theme.BrandOrangeLight

/**
 * 品牌开屏页：承接系统 SplashScreen 之后展示。
 *
 * 衔接关键：系统开屏图标规范为 288dp 画布 / 192dp 可见圆、严格居中于屏幕
 * （未设置 windowSplashScreenIconBackgroundColor，走无背景档）。
 * 这里用同一张带透明边距的 splash_logo_padded 以 288dp 放在屏幕正中，
 * 与系统开屏逐像素对齐——配合 MainActivity 的退场淡出，视觉上就是
 * 同一个图标原地停留，只是下方淡入应用名与标语，不再有跳变。
 *
 * 停留时长由 PidanNavHost 的 splash 路由控制，到时自动进首页。
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    // 图标：轻微淡入 + 微缩放归位，只用于掩盖与系统开屏切换瞬间的亚像素差异；
    // 位置与尺寸全程不动，保证与系统开屏无缝衔接（不再做弹跳放大）
    val iconAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "splashIconAlpha"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.97f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "splashIconScale"
    )
    // 文案：延迟淡入上移
    val textAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 350),
        label = "splashTextAlpha"
    )
    val textOffset by animateFloatAsState(
        targetValue = if (started) 0f else 24f,
        animationSpec = tween(durationMillis = 500, delayMillis = 350),
        label = "splashTextOffset"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BrandOrangeLight, BrandOrangeDark)
                )
            )
    ) {
        val centerY = maxHeight / 2

        // 图标：288dp 画布（可见圆 192dp），圆心严格落在屏幕中心，
        // 与系统 SplashScreen 的图标位置/尺寸完全一致
        Image(
            painter = painterResource(R.drawable.splash_logo_padded),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = centerY - 144.dp)
                .size(288.dp)
                .graphicsLayer {
                    alpha = iconAlpha
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )

        // 文案：锚定在屏幕中心下方（图标可见圆下缘再留 36dp），
        // 独立定位，不影响图标的严格居中
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = centerY + 96.dp + 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                modifier = Modifier
                    .alpha(textAlpha)
                    .graphicsLayer { translationY = textOffset }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.splash_slogan),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                fontSize = 15.sp,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .alpha(textAlpha)
                    .graphicsLayer { translationY = textOffset }
            )
        }
    }
}
