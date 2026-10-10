package com.pidan.health.feature.food.ui

import android.content.Context
import android.widget.FrameLayout
import com.canhub.cropper.CropImageView

/**
 * CropImageView 的容器（仅 OCR 裁剪浮层使用），修复其在 Compose AndroidView 下的布局死循环。
 *
 * 背景：库的 CropImageView.onLayout 在图片加载后每次布局都会把 layoutParams 重新赋值
 * （宽高稳定后仍如此），View.setLayoutParams 会无条件 requestLayout。普通 View 层级里
 * ViewRootImpl 会对布局期间的 requestLayout 去重（最多补一次 traversal），所以库自带
 * Activity 没事；而 Compose AndroidView 会同步重新测量，形成 measure→layout→requestLayout
 * 死循环：主线程被永久占用 → ANR（真机已复现，日志钉死在 applyImageMatrix←onLayout）。
 *
 * 修法：CropImageView 是 final 类无法继承，因此包一层 FrameLayout，在自身布局期间吞掉
 * 子 View 触发的 requestLayout——布局期间子 View 重设的 layoutParams 是 no-op，
 * 吞掉等价于复刻 ViewRootImpl 的去重行为。
 */
class OcrCropHost(context: Context) : FrameLayout(context) {

    val cropView: CropImageView = CropImageView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private var inLayout = false

    init {
        addView(cropView)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        inLayout = true
        super.onLayout(changed, l, t, r, b)
        inLayout = false
    }

    override fun requestLayout() {
        if (inLayout) return
        super.requestLayout()
    }
}
