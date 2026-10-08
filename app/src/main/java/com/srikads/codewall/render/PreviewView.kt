package com.srikads.codewall.render

import android.content.Context
import android.graphics.Canvas
import android.view.View

/** In-app live preview that draws exactly what the wallpaper draws. */
class PreviewView(context: Context) : View(context) {
    private val controller = WallController(context) { postInvalidate() }

    var previewLocked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    init {
        controller.isLocked = { previewLocked }
        controller.renderer.showGuides = true
    }

    fun replayTyping() {
        controller.renderer.startTyping(System.currentTimeMillis())
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller.start()
        controller.renderer.startTyping(System.currentTimeMillis())
    }

    override fun onDetachedFromWindow() {
        controller.stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val next = controller.frame(canvas, width, height)
        postInvalidateDelayed(next)
    }
}
