package com.pidan.health.feature.splash.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
 * 橙色渐变底 + 圆形品牌图标弹性放大 + 应用名/标语淡入，
 * 停留时长由 PidanNavHost 的 splash 路由控制，到时自动进首页。
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    // 图标：从小到大弹性放大，同时淡入
    val iconScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BrandOrangeLight, BrandOrangeDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = iconScale.coerceIn(0f, 1f)
                    }
                    .shadow(elevation = 20.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(24.dp))
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
