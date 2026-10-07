package com.srikads.codewall.wallpaper

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.srikads.codewall.render.WallController

/**
 * The live wallpaper. Set it for "Home and lock screen" so the same live,
 * per-second view shows on the lock screen too. Fields marked "hide on lock"
 * disappear while the keyguard is showing.
 */
class CodeWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = CodeEngine()

    inner class CodeEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val keyguard = getSystemService(KeyguardManager::class.java)
        private val controller = WallController(this@CodeWallpaperService) { handler.post { if (visible) drawFrame() } }
        private val drawRunnable = Runnable { drawFrame() }
        private var visible = false
        private var width = 0
        private var height = 0

        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Personal fields "type in" when the phone is unlocked.
                controller.renderer.startTyping(System.currentTimeMillis())
                if (visible) drawFrame()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            controller.isLocked = { keyguard?.isKeyguardLocked == true }
            ContextCompat.registerReceiver(
                this@CodeWallpaperService, unlockReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunnable)
            controller.stop()
            runCatching { unregisterReceiver(unlockReceiver) }
            super.onDestroy()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                controller.start()
                controller.renderer.startTyping(System.currentTimeMillis())
                drawFrame()
            } else {
                handler.removeCallbacks(drawRunnable)
                controller.stop()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawRunnable)
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPx: Int, yPx: Int) {
            controller.renderer.parallaxOffset = xOffset
            if (visible) drawFrame()
        }

        private fun drawFrame() {
            handler.removeCallbacks(drawRunnable)
            if (width == 0 || height == 0) return
            val holder = surfaceHolder
            var next = 1000L
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull()
                ?: runCatching { holder.lockCanvas() }.getOrNull()
                ?: return
            try {
                next = controller.frame(canvas, width, height)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
            if (visible) handler.postDelayed(drawRunnable, next)
        }
    }
}
