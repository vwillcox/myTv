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
 * It takes the remote's app buttons too ([AppButtons]). Google TV opens their apps from a background service in its
 * own launcher, and once another app is the default home Android blocks those starts, so the buttons did nothing.
 * An accessibility service is allowed to start activities, so the apps are opened here instead.
 *
 * If anything still brings up the Google TV home screen (an app that "goes home" by starting it, say), the window
 * change is seen here and myTV is put in front of it.
 */
class HomeButtonService : AccessibilityService() {
    private var downAt = 0L
    /** App buttons pressed and taken from the system, until they're let go. */
    private val taken = mutableSetOf<Int>()

    override fun onKeyEvent(event: KeyEvent): Boolean {
        AppButtons[event.keyCode]?.let { return appButton(event, it) }
        if (event.keyCode != KeyEvent.KEYCODE_HOME || !app.store.value.overrideHome) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) downAt = event.eventTime
            KeyEvent.ACTION_UP -> if (!event.isCanceled) home(longPress = event.eventTime - downAt > LONG_PRESS_MS)
        }
        // Both halves are kept from the system, or it would see a press that never ends and open its own panel.
        return true
    }

    /**
     * A press is taken only if its app is installed; otherwise the button does what Google TV makes of it. The press
     * opens the app when it's let go. Both halves are kept from the system, as with Home, so Google TV doesn't try to
     * open its own app as well.
     */
    private fun appButton(event: KeyEvent, pkg: String): Boolean {
        val key = event.keyCode
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0 && launchable(pkg)) taken += key
            KeyEvent.ACTION_UP -> return taken.remove(key).also { if (it && !event.isCanceled) app.apps.launch(pkg) }
        }
        return key in taken
    }

    private fun launchable(pkg: String) =
        packageManager.run { getLeanbackLaunchIntentForPackage(pkg) ?: getLaunchIntentForPackage(pkg) } != null

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

        /**
         * The Google TV Streamer remote's app buttons, by the key codes its key layout gives them, and the app each
         * opens. The Netflix button opens AllMedia, and the ★ button (which Google TV lets you set) opens Railcam.
         */
        val AppButtons = mapOf(
            KeyEvent.KEYCODE_BUTTON_3 to "com.google.android.youtube.tv", // YouTube
            KeyEvent.KEYCODE_BUTTON_4 to SystemControl.ALLMEDIA, // Netflix
            KeyEvent.KEYCODE_MACRO_1 to "com.vwillcox.railcam", // ★
        )
    }
}
