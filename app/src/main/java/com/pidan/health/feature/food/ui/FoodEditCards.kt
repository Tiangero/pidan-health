package com.pidan.health.feature.food.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.DashboardCard
import com.pidan.health.core.designsystem.component.SegmentedToggle
import com.pidan.health.core.designsystem.theme.BrandOrangeContainer
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.core.designsystem.theme.OnBrandOrangeContainer
import com.pidan.health.core.designsystem.theme.SubText
import com.pidan.health.feature.food.viewmodel.NutritionField
import java.io.File

/**
 * 新建/编辑食物页的三张表单卡片（对齐设计稿 s-food-add）：
 * 基本信息卡 / 营养成分卡（钠 mg 输入，盐换算预览）/ 价格信息卡（单价只读自动算）。
 * 全部数值解析与校验在 VM，这里只做展示与回调。
 */

/** 通用小节标题（设计稿 .card-title：14px 粗体） */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

/** 数字输入框：小数键盘 + 单行，label 常驻 */
@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        isError = isError,
        supportingText = supportingText?.let { { Text(text = it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

/** 字段标签（设计稿 .field label：13px 次文字色） */
@Composable
private fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = SubText,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

// ---- 卡片一：基本信息 ----

@Composable
fun BasicInfoCard(
    name: String,
    nameInvalid: Boolean,
    onNameChange: (String) -> Unit,
    coverFile: File?,
    isCompressing: Boolean,
    onCoverClick: () -> Unit,
    unitSelectedIndex: Int,
    onUnitSelect: (Int) -> Unit,
    weightPerServingText: String,
    weightPerServingInvalid: Boolean,
    onWeightPerServingChange: (String) -> Unit,
    isByServing: Boolean
) {
    DashboardCard {
        SectionTitle(text = stringResource(R.string.food_edit_section_basic))

        // 食物名称
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.food_edit_name_label)) },
            placeholder = { Text(text = stringResource(R.string.food_edit_name_hint)) },
            isError = nameInvalid,
            supportingText = if (nameInvalid) {
                { Text(text = stringResource(R.string.food_edit_name_invalid)) }
            } else {
                null
            },
            singleLine = true
        )

        // 封面上传口（点击弹来源选择弹层）
        FieldLabel(
            text = stringResource(R.string.food_edit_cover_label),
            modifier = Modifier.padding(top = 14.dp)
        )
        CoverUploadBox(
            coverFile = coverFile,
            isCompressing = isCompressing,
            onClick = onCoverClick
        )

        // 计量单位
        FieldLabel(
            text = stringResource(R.string.food_edit_unit_label),
            modifier = Modifier.padding(top = 14.dp)
        )
        SegmentedToggle(
            options = listOf(
                stringResource(R.string.food_edit_unit_by_gram),
                stringResource(R.string.food_edit_unit_by_serving)
            ),
            selectedIndex = unitSelectedIndex,
            onSelect = onUnitSelect,
            modifier = Modifier.fillMaxWidth()
        )

        // 单份克重（仅按份展示；按份必填）
        if (isByServing) {
            NumberField(
                label = stringResource(R.string.food_edit_serving_label),
                value = weightPerServingText,
                onValueChange = onWeightPerServingChange,
                modifier = Modifier.padding(top = 12.dp),
                isError = weightPerServingInvalid && weightPerServingText.isNotEmpty(),
                supportingText = if (weightPerServingInvalid) {
                    stringResource(R.string.food_edit_serving_invalid)
                } else {
                    stringResource(R.string.food_edit_serving_hint)
                }
            )
        }
    }
}

/** 封面上传口（设计稿 .cover-up）：浅橙底大按钮，有图显示封面预览 */
@Composable
private fun CoverUploadBox(
    coverFile: File?,
    isCompressing: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f)
            .clip(shape)
            .background(if (coverFile != null) Color.Transparent else BrandOrangeContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            isCompressing -> CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = BrandOrangeDark,
                strokeWidth = 3.dp
            )

            coverFile != null -> {
                AsyncImage(
                    model = coverFile,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = stringResource(R.string.food_edit_cover_replace),
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0x66000000))
                        .padding(vertical = 4.dp)
                )
            }

            else -> Text(
                text = stringResource(R.string.food_edit_cover_empty),
                fontSize = 13.sp,
                color = SubText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

// ---- 卡片二：营养成分（按包装基准输入，入库自动归一化为每 100g） ----

@Composable
fun NutritionCard(
    basisGrams: String,
    basisInvalid: Boolean,
    onBasisChange: (String) -> Unit,
    carbs: String,
    protein: String,
    fat: String,
    fiber: String,
    sodiumMg: String,
    saltPreviewText: String?,
    onNutritionChange: (NutritionField, String) -> Unit
) {
    DashboardCard {
        SectionTitle(text = stringResource(R.string.food_edit_section_nutrition))

        // 营养基准量：包装营养表按多少 g 标注就填多少（默认 100），App 自动换算为每 100g
        NumberField(
            label = stringResource(R.string.food_edit_basis_label),
            value = basisGrams,
            onValueChange = onBasisChange,
            isError = basisInvalid,
            supportingText = if (basisInvalid) {
                stringResource(R.string.food_edit_basis_invalid)
            } else {
                stringResource(R.string.food_edit_basis_hint)
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = stringResource(R.string.nutrient_carbs) + " (g)",
                value = carbs,
                onValueChange = { onNutritionChange(NutritionField.CARBS, it) },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = stringResource(R.string.nutrient_protein) + " (g)",
                value = protein,
                onValueChange = { onNutritionChange(NutritionField.PROTEIN, it) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = stringResource(R.string.nutrient_fat) + " (g)",
                value = fat,
                onValueChange = { onNutritionChange(NutritionField.FAT, it) },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = stringResource(R.string.nutrient_fiber) + " (g)",
                value = fiber,
                onValueChange = { onNutritionChange(NutritionField.FIBER, it) },
                modifier = Modifier.weight(1f)
            )
        }

        // 盐：按包装「钠 mg」输入，实时换算盐 g 预览（入库口径：每 100g）
        NumberField(
            label = stringResource(R.string.food_edit_sodium_label),
            value = sodiumMg,
            onValueChange = { onNutritionChange(NutritionField.SODIUM, it) },
            supportingText = saltPreviewText
        )
    }
}

// ---- 卡片三：价格信息 ----

@Composable
fun PriceCard(
    priceText: String,
    purchaseAmountText: String,
    purchaseUnitSelectedIndex: Int,
    amountLabel: String,
    priceInvalid: Boolean,
    priceInvalidText: String,
    onPriceChange: (String) -> Unit,
    onPurchaseAmountChange: (String) -> Unit,
    onPurchaseUnitSelect: (Int) -> Unit,
    unitPriceText: String
) {
    DashboardCard {
        SectionTitle(text = stringResource(R.string.food_edit_section_price))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = stringResource(R.string.food_edit_price_label),
                value = priceText,
                onValueChange = onPriceChange,
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = amountLabel,
                value = purchaseAmountText,
                onValueChange = onPurchaseAmountChange,
                modifier = Modifier.weight(1f),
                isError = priceInvalid,
                supportingText = if (priceInvalid) priceInvalidText else null
            )
        }

        // 购买总量输入单位：按 g / 按份（按份自动 × 单份克重折算成克入库）
        SegmentedToggle(
            options = listOf(
                stringResource(R.string.food_edit_unit_by_gram),
                stringResource(R.string.food_edit_unit_by_serving)
            ),
            selectedIndex = purchaseUnitSelectedIndex,
            onSelect = onPurchaseUnitSelect,
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
        )

        // 只读单价：随金额/总量实时重算
        OutlinedTextField(
            value = unitPriceText,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            label = { Text(text = stringResource(R.string.food_edit_unit_price_label)) },
            readOnly = true,
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = OnBrandOrangeContainer,
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            enabled = false
        )
    }
}
