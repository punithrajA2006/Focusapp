package com.focuslock.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.focuslock.FocusLockApplication
import com.focuslock.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the full-screen non-dismissable TYPE_APPLICATION_OVERLAY view
 * that immediately blankets unapproved applications.
 */
class BlockOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val isOverlayShowing = AtomicBoolean(false)

    @SuppressLint("InflateParams")
    fun showBlockOverlay(blockedPackage: String, remainingMinutes: Int) {
        if (isOverlayShowing.get()) {
            updateBlockedPackageInfo(blockedPackage, remainingMinutes)
            return
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            val view = createOverlayView(blockedPackage, remainingMinutes)
            windowManager.addView(view, layoutParams)
            overlayView = view
            isOverlayShowing.set(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissBlockOverlay() {
        if (!isOverlayShowing.get()) return
        try {
            overlayView?.let { windowManager.removeView(it) }
            overlayView = null
            isOverlayShowing.set(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createOverlayView(blockedPackage: String, remainingMinutes: Int): View {
        val root = View.inflate(context, android.R.layout.simple_list_item_1, null)
        // Programmatic fallback view creation in case XML layout isn't inflated
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#090D16"))
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(context).apply {
            text = "Back to Study"
            textSize = 28f
            setTextColor(android.graphics.Color.parseColor("#EF4444"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitleView = TextView(context).apply {
            tag = "BLOCKED_INFO"
            text = "Application blocked by FocusLock:\n$blockedPackage\n\nRemaining study time: ~${remainingMinutes}m"
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 48)
        }

        val returnButton = Button(context).apply {
            text = "Return to Allowed Apps"
            setBackgroundColor(android.graphics.Color.parseColor("#38BDF8"))
            setTextColor(android.graphics.Color.parseColor("#000000"))
            setPadding(32, 16, 32, 16)
            setOnClickListener {
                // Navigate back to home launcher
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
                dismissBlockOverlay()
            }
        }

        layout.addView(titleView)
        layout.addView(subtitleView)
        layout.addView(returnButton)

        return layout
    }

    private fun updateBlockedPackageInfo(blockedPackage: String, remainingMinutes: Int) {
        overlayView?.findViewWithTag<TextView>("BLOCKED_INFO")?.text =
            "Application blocked by FocusLock:\n$blockedPackage\n\nRemaining study time: ~${remainingMinutes}m"
    }
}
