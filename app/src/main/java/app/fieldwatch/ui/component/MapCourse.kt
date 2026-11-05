package app.fieldwatch.ui.component

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import app.fieldwatch.domain.MapFrame
import app.fieldwatch.domain.SitPathPlot
import kotlin.math.abs

/**
 * The heading drawn on the map. A new GPS course arrives about once a second.
 * The picture turns through that gap, and the ground is laid out at the angle
 * on screen so the edges stay filled.
 */
@Composable
internal fun rememberEasedCourse(target: Double): Double {
    val anim = remember {
        Animatable(if (target.isFinite()) target.toFloat() else Float.NaN)
    }
    val lastAt = remember { mutableLongStateOf(0L) }
    LaunchedEffect(target) {
        if (!target.isFinite()) {
            anim.snapTo(Float.NaN)
            lastAt.longValue = 0L
            return@LaunchedEffect
        }
        val from = anim.value.toDouble()
        val now = SystemClock.uptimeMillis()
        val gap = if (lastAt.longValue == 0L) 0L else now - lastAt.longValue
        lastAt.longValue = now
        if (!from.isFinite()) {
            anim.snapTo(target.toFloat())
            return@LaunchedEffect
        }
        val turn = MapFrame.shortestTurn(from, target)
        if (abs(turn) < 1.0) {
            anim.snapTo(wrapCourse(target).toFloat())
        } else {
            anim.animateTo(
                (from + turn).toFloat(),
                tween(
                    durationMillis = MapFrame.courseTurnMs(gap),
                    easing = LinearEasing,
                ),
            )
            anim.snapTo(wrapCourse(anim.value.toDouble()).toFloat())
        }
    }
    if (!target.isFinite()) return Double.NaN
    val shown = anim.value.toDouble()
    return if (shown.isFinite()) wrapCourse(shown) else target
}

/**
 * Settled heading for the live path. The picture is laid out here, and
 * [screenTurnDeg] rotates it while the heading eases. Reading [shownDeg] during
 * composition redraws that caller on every frame of the turn.
 */
internal class CourseMotion {
    val anim = Animatable(Float.NaN)
    var target: Double = Double.NaN
    val padPx = mutableFloatStateOf(0f)

    fun shownDeg(): Double {
        if (!target.isFinite()) return Double.NaN
        val shown = anim.value.toDouble()
        return if (shown.isFinite()) wrapCourse(shown) else target
    }

    /** Clockwise degrees that turn a picture laid out at [target] onto the eased heading. */
    fun screenTurnDeg(): Float {
        val shown = shownDeg()
        if (!shown.isFinite() || !target.isFinite()) return 0f
        val delta = MapFrame.shortestTurn(target, shown)
        if (abs(delta) < 0.05) return 0f
        return (-delta).toFloat()
    }
}

@Composable
internal fun rememberCourseMotion(target: Double, widthPx: Float, heightPx: Float): CourseMotion {
    val motion = remember { CourseMotion() }
    val lastAt = remember { mutableLongStateOf(0L) }
    val size = remember { floatArrayOf(0f, 0f) }
    val seenBits = remember { mutableLongStateOf(Long.MIN_VALUE) }
    size[0] = widthPx
    size[1] = heightPx
    motion.target = target
    val bits = if (target.isFinite()) target.toRawBits() else 0x7FF8000000000000L
    if (seenBits.longValue != bits) {
        val from = motion.anim.value.toDouble()
        val turn = if (from.isFinite() && target.isFinite()) abs(MapFrame.shortestTurn(from, target)) else 0.0
        motion.padPx.floatValue = SitPathPlot.rotationPadPx(turn, size[0] / 2f, size[1] / 2f)
        seenBits.longValue = bits
    }
    LaunchedEffect(target) {
        easeToward(motion.anim, target, lastAt)
        motion.padPx.floatValue = 0f
    }
    return motion
}

private suspend fun easeToward(
    anim: Animatable<Float, AnimationVector1D>,
    target: Double,
    lastAt: androidx.compose.runtime.MutableLongState,
) {
    if (!target.isFinite()) {
        anim.snapTo(Float.NaN)
        lastAt.longValue = 0L
        return
    }
    val from = anim.value.toDouble()
    val now = SystemClock.uptimeMillis()
    val gap = if (lastAt.longValue == 0L) 0L else now - lastAt.longValue
    lastAt.longValue = now
    if (!from.isFinite()) {
        anim.snapTo(target.toFloat())
        return
    }
    val turn = MapFrame.shortestTurn(from, target)
    if (abs(turn) < 1.0) {
        anim.snapTo(wrapCourse(target).toFloat())
    } else {
        anim.animateTo(
            (from + turn).toFloat(),
            tween(
                durationMillis = MapFrame.courseTurnMs(gap),
                easing = LinearEasing,
            ),
        )
        anim.snapTo(wrapCourse(anim.value.toDouble()).toFloat())
    }
}

private fun wrapCourse(deg: Double): Double {
    val turned = deg % 360.0
    return if (turned < 0.0) turned + 360.0 else turned
}
