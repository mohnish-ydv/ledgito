package com.mohnishraj.goldmineledger

import android.animation.TimeInterpolator
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.core.view.children

/** Small, dependency-free motion and feedback helpers used across the polished experience. */
private val MATERIAL_STANDARD_EASING: TimeInterpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)

fun View.playScreenEntrance(
    distanceDp: Float = 12f,
    durationMs: Long = 260L,
    interpolator: TimeInterpolator = MATERIAL_STANDARD_EASING
) {
    if (isInEditMode) return
    val distance = distanceDp * resources.displayMetrics.density
    alpha = 0f
    translationY = distance
    animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(durationMs)
        .setInterpolator(interpolator)
        .start()
}

fun ViewGroup.playStaggeredEntrance(maxChildren: Int = 8) {
    children.take(maxChildren).forEachIndexed { index, child ->
        child.alpha = 0f
        child.translationY = 10f * resources.displayMetrics.density
        child.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(index * 34L)
            .setDuration(250L)
            .setInterpolator(MATERIAL_STANDARD_EASING)
            .start()
    }
}

fun View.confirmHaptic() {
    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

fun View.rejectHaptic() {
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}
