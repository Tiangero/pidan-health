package com.pidan.health.feature.food.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pidan.health.R
import com.pidan.health.core.designsystem.component.AppTopBar
import com.pidan.health.core.designsystem.component.AppTopBarTitle
import com.pidan.health.core.designsystem.theme.BrandOrange
import com.pidan.health.core.designsystem.theme.BrandOrangeContainer
import com.pidan.health.core.designsystem.theme.BrandOrangeDark
import com.pidan.health.feature.food.viewmodel.FoodItemUi
import com.pidan.health.feature.food.viewmodel.FoodLibraryViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 食物库页：单列列表（封面 / 名称 / 单位标识 / 快捷录入「+」）+ 底部「新建食物」。
 * 快捷录入走场景 B 固化计算写 diet_record，首页卡片响应式即时刷新。
 * 「新建食物」/ 点击列表项 → T6 新建/编辑页（foodId 区分模式）。
 * 顶栏「管理」进入管理模式（T7）：多选 + 批量删除，隐藏「+」与「新建食物」，
 * 底部操作栏（全选 / 删除 / 完成），删除前有防呆确认。
 */
@Composable
fun FoodLibraryScreen(
    onNavigateToFoodEdit: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FoodLibraryViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val successMessage by viewModel.entrySuccessMessage.collectAsStateWithLifecycle()
    val deleteSuccessCount by viewModel.deleteSuccessCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 录入成功提示
    LaunchedEffect(successMessage) {
        successMessage?.let { name ->
            snackbarHostState.showSnackbar(
                context.getString(R.string.quick_entry_success, name)
            )
            viewModel.consumeEntrySuccessMessage()
        }
    }

    // 批量删除成功提示
    LaunchedEffect(deleteSuccessCount) {
        deleteSuccessCount?.let { count ->
            snackbarHostState.showSnackbar(
                context.getString(R.string.food_manage_delete_success, count)
            )
            viewModel.consumeDeleteSuccessCount()
        }
    }

    // 「新建食物」→ 编辑页（foodId=null 新建模式）
    val onNewFoodClick: () -> Unit = { onNavigateToFoodEdit(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = {
                    if (state.isManageMode) {
                        AppTopBarTitle(
                            stringResource(R.string.food_manage_selected_count, state.selectedIds.size)
                        )
                    } else {
                        AppTopBarTitle(stringResource(R.string.topbar_title_food))
                    }
                },
                actions = {
                    if (!state.isManageMode && state.items.isNotEmpty()) {
                        TextButton(onClick = viewModel::enterManageMode) {
                            Text(
                                text = stringResource(R.string.topbar_action_manage),
                                style = MaterialTheme.typography.labelLarge,
                                color = BrandOrangeDark
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isInitialLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                state.items.isEmpty() -> EmptyLibrary(
                    onNewFoodClick = onNewFoodClick,
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> FoodList(
                    items = state.items,
                    isManageMode = state.isManageMode,
                    selectedIds = state.selectedIds,
                    onQuickEntry = viewModel::startQuickEntry,
                    onToggleSelect = viewModel::toggleSelected,
                    onItemClick = { onNavigateToFoodEdit(it.id) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 底部操作区：管理模式显示全选/删除/完成操作栏，否则显示「新建食物」
            when {
                state.isManageMode && state.items.isNotEmpty() -> ManageActionBar(
                    isAllSelected = state.selectedIds.containsAll(state.items.map { it.id }.toSet()) &&
                        state.items.isNotEmpty(),
                    hasSelection = state.selectedIds.isNotEmpty(),
                    onSelectAll = viewModel::toggleSelectAll,
                    onDelete = viewModel::requestDeleteSelected,
                    onDone = viewModel::exitManageMode,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )

                state.items.isNotEmpty() -> NewFoodButton(
                    onClick = onNewFoodClick,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }

    state.entryTarget?.let { target ->
        QuickEntryDialog(
            item = target,
            onConfirm = viewModel::confirmQuickEntry,
            onDismiss = viewModel::dismissQuickEntry
        )
    }

    // 批量删除防呆确认（M3 AlertDialog）
    if (state.isDeleteConfirmVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text(stringResource(R.string.food_manage_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.food_manage_delete_confirm_text,
                        state.selectedIds.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteSelected) {
                    Text(
                        text = stringResource(R.string.food_manage_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirm) {
                    Text(text = stringResource(R.string.weight_dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun FoodList(
    items: List<FoodItemUi>,
    isManageMode: Boolean,
    selectedIds: Set<Long>,
    onQuickEntry: (FoodItemUi) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onItemClick: (FoodItemUi) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.id }) { food ->
            FoodItemRow(
                food = food,
                isManageMode = isManageMode,
                isSelected = food.id in selectedIds,
                onQuickEntry = onQuickEntry,
                onToggleSelect = { onToggleSelect(food.id) },
                onClick = { onItemClick(food) }
            )
        }
    }
}

/**
 * 列表行（对齐设计稿 .food-item）：白卡 + 封面 + 名称/单位标识 + 「+」；点击行进编辑页。
 * 管理模式：行首出现 M3 Checkbox（多选），隐藏「+」；点击行内容仍跳 T6 编辑页（单选编辑）。
 */
@Composable
private fun FoodItemRow(
    food: FoodItemUi,
    isManageMode: Boolean,
    isSelected: Boolean,
    onQuickEntry: (FoodItemUi) -> Unit,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) BrandOrangeContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surface
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isManageMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(checkedColor = BrandOrange)
            )
        }

        FoodCover(food = food)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = food.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = food.unitLabel,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // 「+」快捷录入（对齐设计稿 .add-btn：32dp 圆角方块，品牌浅底 + 品牌深字）；管理模式隐藏
        if (!isManageMode) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandOrangeContainer)
                    .clickable { onQuickEntry(food) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = BrandOrangeDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 管理模式底部操作栏：全选 / 删除 / 完成 */
@Composable
private fun ManageActionBar(
    isAllSelected: Boolean,
    hasSelection: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onSelectAll) {
            Text(
                text = if (isAllSelected) {
                    stringResource(R.string.food_manage_deselect_all)
                } else {
                    stringResource(R.string.food_manage_select_all)
                },
                color = BrandOrangeDark,
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onDelete,
            enabled = hasSelection,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(text = stringResource(R.string.food_manage_delete))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Button(
            onClick = onDone,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
        ) {
            Text(text = stringResource(R.string.food_manage_done))
        }
    }
}

/** 封面：有图用 Coil 加载沙盒文件，无图用首字占位色块（颜色由名称哈希稳定选取） */
@Composable
private fun FoodCover(food: FoodItemUi) {
    val coverShape = RoundedCornerShape(8.dp)
    if (food.coverFile != null) {
        AsyncImage(
            model = food.coverFile,
            contentDescription = food.name,
            modifier = Modifier
                .size(46.dp)
                .clip(coverShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(coverShape)
                .background(placeholderColor(food.name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = food.name.take(1),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 占位色板（对齐设计稿 coverColors，按名称哈希稳定取色） */
private val CoverPalette = listOf(
    Color(0xFFF97316), Color(0xFFD97706), Color(0xFF65A30D),
    Color(0xFF0D9488), Color(0xFF7C3AED), Color(0xFFDB2777)
)

private fun placeholderColor(name: String): Color =
    CoverPalette[kotlin.math.abs(name.hashCode()) % CoverPalette.size]

@Composable
private fun EmptyLibrary(
    onNewFoodClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.food_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        NewFoodButton(onClick = onNewFoodClick)
    }
}

@Composable
private fun NewFoodButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
    ) {
        Text(
            text = stringResource(R.string.food_new),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
