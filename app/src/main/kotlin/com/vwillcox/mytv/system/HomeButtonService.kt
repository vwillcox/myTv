package com.vwillcox.mytv.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.vwillcox.mytv.MainActivity
import com.vwillcox.mytv.app

/**
 * Google TV keeps its own home screen behind the Home button whichever app is the default home, so this service
 * takes the button first. A short press opens myTV; a long press opens myTV's settings panel, standing in for the
 * Google TV panel the same press would have opened.
 *
 * If anything still brings up the Google TV home screen (an app that "goes home" by starting it, say), the window
 * change is seen here and myTV is put in front of it.
 */
class HomeButtonService : AccessibilityService() {
    private var downAt = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || !app.store.value.overrideHome) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) downAt = event.eventTime
            KeyEvent.ACTION_UP -> if (!event.isCanceled) home(longPress = event.eventTime - downAt > LONG_PRESS_MS)
        }
        // Both halves are kept from the system, or it would see a press that never ends and open its own panel.
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !app.store.value.overrideHome) return
        if (event.packageName == STOCK_LAUNCHER && event.className?.toString()?.endsWith(".HomeActivity") == true) home(longPress = false)
    }

    private fun home(longPress: Boolean) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = if (longPress) MainActivity.ACTION_PANEL else Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        })
    }

    override fun onInterrupt() {}

    companion object {
        const val STOCK_LAUNCHER = "com.google.android.apps.tv.launcherx"
        private const val LONG_PRESS_MS = 600L
    }
}
