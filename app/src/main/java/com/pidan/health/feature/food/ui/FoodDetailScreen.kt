package com.pidan.health.feature.food.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.AppTopBar
import com.pidan.health.core.designsystem.component.AppTopBarTitle
import com.pidan.health.core.designsystem.component.DashboardCard
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.core.domain.FoodUnit
import com.pidan.health.feature.food.viewmodel.FoodDetailViewModel
import java.io.File
import org.koin.androidx.compose.koinViewModel

/**
 * 食物详情页（只读）：封面 + 基本信息 + 营养成分（每 100g）+ 价格信息。
 * 与编辑页（FoodEditScreen）彻底分离：查看走本页，修改经顶栏「编辑」进编辑页。
 * 模板响应式观察，编辑保存返回后自动刷新。
 */
@Composable
fun FoodDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FoodDetailViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 模板不存在（如被批量删除）→ 直接退出
    LaunchedEffect(state.isMissing) {
        if (state.isMissing) onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = { AppTopBarTitle(stringResource(R.string.topbar_title_food_detail)) },
                onBack = onBack,
                actions = {
                    if (!state.isLoading && !state.isMissing) {
                        TextButton(onClick = onEdit) {
                            Text(
                                text = stringResource(R.string.action_edit),
                                style = MaterialTheme.typography.labelLarge,
                                color = BrandOrangeDark
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandOrangeDark)
            }
            return@Scaffold
        }

        // 委托属性无法 smart cast，先解出局部值
        val totalAmountText = state.totalAmountText
        val unitPriceText = state.unitPriceText

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 封面（无图用首字占位色块，与食物库列表同一视觉）
            DetailCover(coverFile = state.coverFile, name = state.name)

            Text(
                text = state.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            DashboardCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.food_edit_section_basic),
                        style = MaterialTheme.typography.titleMedium
                    )
                    DetailRow(
                        label = stringResource(R.string.food_edit_unit_label),
                        value = stringResource(
                            if (state.unitType == FoodUnit.BY_SERVING) R.string.food_edit_unit_by_serving
                            else R.string.food_edit_unit_by_gram
                        )
                    )
                    val servingText = state.weightPerServingText
                    if (state.unitType == FoodUnit.BY_SERVING && servingText != null) {
                        DetailRow(
                            label = stringResource(R.string.food_detail_serving_label),
                            value = servingText + "g"
                        )
                    }
                }
            }

            DashboardCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.food_detail_nutrition_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    DetailRow(
                        label = stringResource(R.string.nutrient_carbs),
                        value = state.carbsText + "g"
                    )
                    DetailRow(
                        label = stringResource(R.string.nutrient_protein),
                        value = state.proteinText + "g"
                    )
                    DetailRow(
                        label = stringResource(R.string.nutrient_fat),
                        value = state.fatText + "g"
                    )
                    DetailRow(
                        label = stringResource(R.string.nutrient_fiber),
                        value = state.fiberText + "g"
                    )
                    DetailRow(
                        label = stringResource(R.string.nutrient_salt),
                        value = buildString {
                            append(state.saltText)
                            append("g")
                            state.sodiumMgText?.let { sodium ->
                                append("（")
                                append(stringResource(R.string.food_detail_sodium_note, sodium))
                                append("）")
                            }
                        }
                    )
                }
            }

            DashboardCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.food_edit_section_price),
                        style = MaterialTheme.typography.titleMedium
                    )
                    DetailRow(
                        label = stringResource(R.string.food_edit_price_label),
                        value = state.totalPriceText
                            ?: stringResource(R.string.food_detail_price_empty)
                    )
                    if (totalAmountText != null) {
                        DetailRow(
                            label = stringResource(R.string.food_detail_total_amount_label),
                            value = totalAmountText
                        )
                    }
                    if (unitPriceText != null) {
                        DetailRow(
                            label = stringResource(R.string.food_edit_unit_price_label),
                            value = unitPriceText
                        )
                    }
                }
            }
        }
    }
}

/** 详情封面：有图 Coil 加载，无图首字占位色块（与列表页 CoverPalette 同一色板） */
@Composable
private fun DetailCover(coverFile: File?, name: String) {
    val coverShape = RoundedCornerShape(16.dp)
    if (coverFile != null) {
        AsyncImage(
            model = coverFile,
            contentDescription = name,
            modifier = Modifier
                .size(140.dp)
                .clip(coverShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(coverShape)
                .background(CoverPalette[placeholderIndex(name)]),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun placeholderIndex(name: String): Int =
    kotlin.math.abs(name.hashCode()) % CoverPalette.size

/** 详情页只读行：标签左、数值右 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
