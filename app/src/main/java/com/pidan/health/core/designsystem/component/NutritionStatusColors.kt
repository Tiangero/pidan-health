package com.pidan.health.core.designsystem.component

import androidx.compose.ui.graphics.Color
import com.pidan.health.core.domain.NutritionStatus

/**
 * 判定状态 → 语义色（红绿橙为产品固定语义，不随主题切换）。
 * 色值对齐设计稿 CSS 变量 --ok/--under/--over 及对应浅底。
 * 首页营养卡片、统计页等所有按判定引擎着色的位置统一使用，禁止各处自配色。
 */
object NutritionStatusColors {
    val Ok = Color(0xFF16A34A)          // 绿：达标
    val OkContainer = Color(0xFFEAF6EE)
    val Under = Color(0xFFF97316)       // 橙：未达标
    val UnderContainer = Color(0xFFFFF3E8)
    val Over = Color(0xFFDC2626)        // 红：超标
    val OverContainer = Color(0xFFFDECEC)
}

fun NutritionStatus.statusColor(): Color = when (this) {
    NutritionStatus.OK -> NutritionStatusColors.Ok
    NutritionStatus.UNDER -> NutritionStatusColors.Under
    NutritionStatus.OVER -> NutritionStatusColors.Over
}

/** 状态药丸的浅色底（设计稿 .state-pill 的 st-ok/st-under/st-over 背景） */
fun NutritionStatus.statusContainerColor(): Color = when (this) {
    NutritionStatus.OK -> NutritionStatusColors.OkContainer
    NutritionStatus.UNDER -> NutritionStatusColors.UnderContainer
    NutritionStatus.OVER -> NutritionStatusColors.OverContainer
}
