package com.mohnishraj.goldmineledger

import android.app.Activity
import android.graphics.Color
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Keeps every screen clear of status bars, display cut-outs and gesture/navigation bars.
 * Android 15 enforces edge-to-edge for targetSdk 35, so relying on theme padding alone is
 * not sufficient. The original view padding is retained and the safe insets are added once.
 */
@Suppress("DEPRECATION")
fun Activity.applySafeSystemBars(root: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT

    val initialLeft = root.paddingLeft
    val initialTop = root.paddingTop
    val initialRight = root.paddingRight
    val initialBottom = root.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val safe = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(
            initialLeft + safe.left,
            initialTop + safe.top,
            initialRight + safe.right,
            initialBottom + safe.bottom
        )
        // Keep IME and other inset types available to child views while the activity owns
        // only the system-bar/cut-out padding.
        insets
    }
    ViewCompat.requestApplyInsets(root)
}
