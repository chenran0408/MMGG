package com.leaf.hyperdragshare.codex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** A short-lived return action owned by the module's existing overlay permission. */
internal object StashSavedNotice {
    private val main = Handler(Looper.getMainLooper())
    private var active: Notice? = null

    fun show(app: Context, count: Int) {
        val notice = active ?: Notice(app).also { active = it }
        notice.show(count)
    }

    fun dismiss() {
        active?.overlay?.dismiss()
    }

    private class Notice(private val app: Context) {
        private val display = requireNotNull(app.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY))
        private val context = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        private var count by mutableIntStateOf(0)
        private val timeout = Runnable { overlay.dismiss() }
        private val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = overlay.dismiss()
        }
        private val dark = DragShareSettings.readLocal(app).isDarkTheme(context)
        private val theme = ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        val overlay: InteractiveOverlayWindow = InteractiveOverlayWindow(context, OverlayWindowPolicy.application(),
            onDismissed = {
                main.removeCallbacks(timeout)
                app.unregisterReceiver(screenOff)
                if (active === this) active = null
            },
            onFailure = {
                DragShareLog.w("DragShare/Stash", "Unable to display saved notice", it)
                Toast.makeText(app, "已存入中转站", Toast.LENGTH_SHORT).show()
            },
        ) {
            MiuixTheme(controller = theme) {
                TextButton(
                    text = "已存入 $count 项 · 打开",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = ::open,
                )
            }
        }

        init {
            val position = app.getSharedPreferences("stash_window_position", Context.MODE_PRIVATE)
            val metrics = context.getSystemService(WindowManager::class.java).maximumWindowMetrics
            val safe = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val left = position.getBoolean("left", false)
            val anchor = position.getFloat("center_y", .5f)
            val density = context.resources.displayMetrics.density
            val height = (48 * density).toInt()
            overlay.window.attributes = overlay.window.attributes.apply {
                width = (176 * density).toInt().coerceAtMost(metrics.bounds.width() - safe.left - safe.right)
                this.height = height
                gravity = Gravity.TOP or if (left) Gravity.LEFT else Gravity.RIGHT
                x = (if (left) safe.left else safe.right) + (8 * density).toInt()
                y = (metrics.bounds.height() * anchor - height / 2).toInt()
                    .coerceIn(safe.top, (metrics.bounds.height() - safe.bottom - height).coerceAtLeast(safe.top))
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            app.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        }

        fun show(added: Int) {
            count += added
            main.removeCallbacks(timeout)
            if (overlay.show()) main.postDelayed(timeout, 3_000L)
        }

        private fun open() {
            overlay.dismiss()
            StashOverlayPanel.show(app)
        }
    }
}
