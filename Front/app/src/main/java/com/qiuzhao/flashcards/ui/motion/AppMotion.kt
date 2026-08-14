package com.qiuzhao.flashcards.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Stable Compose equivalents for Material emphasized motion; no alpha MotionScheme API. */
object AppMotion {
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    fun <T> enter(): FiniteAnimationSpec<T> = tween(durationMillis = 300, easing = EmphasizedDecelerate)
    fun <T> exit(): FiniteAnimationSpec<T> = tween(durationMillis = 180, easing = Emphasized)

    /**
     * Material 3 emphasized-decelerate motion for a departing control that
     * expands into its destination surface (a container transform).
     */
    fun <T> containerTransformEnter(durationMillis: Int = 640): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EmphasizedDecelerate)

    fun <T> emphasisSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
