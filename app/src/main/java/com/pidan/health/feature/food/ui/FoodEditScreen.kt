package com.pidan.health.feature.food.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.AppTopBar
import com.pidan.health.core.designsystem.component.AppTopBarTitle
import com.pidan.health.core.designsystem.theme.BrandOrange
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.core.domain.FoodUnit
import com.pidan.health.feature.food.viewmodel.FoodEditViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 新建/编辑食物页（T6）：新建与编辑复用同一路由（foodId 缺省为新建）。
 * 表单三卡片 + 封面（相机/相册 → 强制压缩）+ 保存（校验在 VM）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditScreen(
    onBack: () -> Unit,
    viewModel: FoodEditViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // ---- 封面来源：相册（Photo Picker，无需权限）/ 相机（FileProvider 临时文件） ----
    var showCoverSheet by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onImagePicked(it.toString()) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraUri?.let { viewModel.onImagePicked(it.toString()) }
    }

    // 压缩失败提示（一次性消费）
    LaunchedEffect(state.coverFailed) {
        if (state.coverFailed) {
            snackbarHostState.showSnackbar(context.getString(R.string.food_edit_cover_failed))
            viewModel.consumeCoverError()
        }
    }

    // 保存成功 → 返回列表页（列表 observeAll 响应式即时刷新）
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = {
                    AppTopBarTitle(
                        stringResource(
                            if (state.isEdit) R.string.food_edit_title_edit
                            else R.string.food_edit_title_new
                        )
                    )
                },
                onBack = onBack
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandOrange)
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BasicInfoCard(
                    name = state.name,
                    nameInvalid = state.name.isNotEmpty() && !viewModel.isNameValid,
                    onNameChange = viewModel::onNameChange,
                    coverFile = state.pendingCoverFile ?: state.existingCoverFile,
                    isCompressing = state.isCompressing,
                    onCoverClick = { showCoverSheet = true },
                    unitSelectedIndex = state.unitType,
                    onUnitSelect = viewModel::onUnitTypeSelect,
                    weightPerServingText = state.weightPerServingText,
                    weightPerServingInvalid = !viewModel.isServingValid,
                    onWeightPerServingChange = viewModel::onWeightPerServingChange,
                    isByServing = state.unitType == FoodUnit.BY_SERVING
                )

                NutritionCard(
                    basisGrams = state.basisGramsText,
                    basisInvalid = state.basisGramsText.isNotEmpty() && !viewModel.isBasisValid,
                    onBasisChange = viewModel::onBasisGramsChange,
                    carbs = state.carbsText,
                    protein = state.proteinText,
                    fat = state.fatText,
                    fiber = state.fiberText,
                    sodiumMg = state.sodiumMgText,
                    saltPreviewText = viewModel.saltPreviewGrams?.let {
                        stringResource(R.string.food_edit_sodium_preview, formatPreview(it))
                    },
                    onNutritionChange = viewModel::onNutritionChange
                )

                PriceCard(
                    priceText = state.priceText,
                    purchaseAmountText = state.purchaseAmountText,
                    purchaseUnitSelectedIndex = state.purchaseUnit,
                    amountLabel = stringResource(
                        if (state.purchaseUnit == FoodUnit.BY_SERVING) R.string.food_edit_amount_label_by_serving
                        else R.string.food_edit_amount_label_by_gram
                    ),
                    priceInvalid = !viewModel.isPriceValid,
                    priceInvalidText = stringResource(R.string.food_edit_price_invalid),
                    onPriceChange = viewModel::onPriceChange,
                    onPurchaseAmountChange = viewModel::onPurchaseAmountChange,
                    onPurchaseUnitSelect = viewModel::onPurchaseUnitSelect,
                    unitPriceText = viewModel.unitPricePer100g?.let {
                        stringResource(R.string.food_edit_unit_price_value, formatPreview(it))
                    } ?: stringResource(R.string.food_edit_unit_price_empty)
                )

                SaveButton(
                    enabled = viewModel.canSave,
                    isSaving = state.isSaving,
                    onClick = viewModel::save
                )
            }
        }
    }

    if (showCoverSheet) {
        CoverSourceSheet(
            onCamera = {
                showCoverSheet = false
                val uri = viewModel.cameraCaptureUri()
                cameraUri = uri
                cameraLauncher.launch(uri)
            },
            onGallery = {
                showCoverSheet = false
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDismiss = { showCoverSheet = false }
        )
    }
}

@Composable
private fun SaveButton(
    enabled: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = stringResource(R.string.food_edit_save))
            }
        }
        if (!enabled && !isSaving) {
            Text(
                text = stringResource(R.string.food_edit_save_invalid),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 单价 / 盐换算等预览数字：最多保留两位小数并去尾零 */
private fun formatPreview(value: Float): String = FoodEditViewModel.formatNumber(value)

/** 封面来源弹层（对齐设计稿 .sheet）：拍照上传 / 从相册选择 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverSourceSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.food_edit_cover_label),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.food_edit_cover_sheet_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CoverSourceOption(
                icon = { Icon(Icons.Rounded.PhotoCamera, null, tint = BrandOrangeDark) },
                label = stringResource(R.string.food_edit_cover_camera),
                onClick = onCamera
            )
            CoverSourceOption(
                icon = { Icon(Icons.Rounded.PhotoLibrary, null, tint = BrandOrangeDark) },
                label = stringResource(R.string.food_edit_cover_gallery),
                onClick = onGallery
            )
        }
    }
}

@Composable
private fun CoverSourceOption(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon()
        Text(
            text = label,
            fontSize = 15.sp,
            color = BrandOrangeDark
        )
    }
}
