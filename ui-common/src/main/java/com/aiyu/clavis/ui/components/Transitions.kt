package com.aiyu.clavis.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.aiyu.clavis.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Keys for elements that travel between screens. */
object SharedKeys {
    /** The brand mark: landing hero -> setup -> lock -> home header. One continuous object. */
    const val MARK = "clavis-mark"
    const val TITLE = "clavis-title"
    const val ADD_CARD = "clavis-add-card"
    fun card(id: String) = "card-$id"
}

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Shared bounds (container transform) inside the nav shared scope; no-op in previews or with reduced motion. */
@Composable
fun Modifier.sharedBoundsOrSelf(key: String, clipShape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalNavAnimatedScope.current ?: return this
    if (rememberReducedMotion()) return this
    return with(shared) {
        this@sharedBoundsOrSelf.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            // A slightly soft spring so the eye can follow the element between screens.
            boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 280f) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            clipInOverlayDuringTransition = OverlayClip(clipShape),
        )
    }
}

/** Shared element (same content, moving bounds): the brand mark and titles. */
@Composable
fun Modifier.sharedElementOrSelf(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalNavAnimatedScope.current ?: return this
    if (rememberReducedMotion()) return this
    return with(shared) {
        this@sharedElementOrSelf.sharedElement(
            rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.8f, stiffness = 220f) },
        )
    }
}

/** True while a shared transition runs; FABs and overlays hide so the moving element does not slide over them. */
@Composable
fun isSharedTransitionRunning(): Boolean = LocalSharedTransitionScope.current?.isTransitionActive == true

/**
 * Follows the system predictive-back gesture: content shrinks and slides toward the swipe edge while the finger is
 * down, [onBack] runs on commit, a cancelled gesture springs back. Reduced motion skips the transform.
 */
@Composable
fun PredictiveBack(enabled: Boolean = true, onBack: () -> Unit, content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(0f) }
    var fromRight by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { e: BackEventCompat ->
                fromRight = e.swipeEdge == BackEventCompat.EDGE_RIGHT
                if (!reduced) progress.snapTo(e.progress)
            }
            onBack()
            progress.snapTo(0f)
        } catch (c: CancellationException) {
            scope.launch { progress.animateTo(0f, spring) }
            throw c
        }
    }

    val active = progress.value != 0f
    Box(
        if (!active) Modifier else Modifier
            .graphicsLayer {
                val p = progress.value
                val s = 1f - 0.1f * p
                scaleX = s
                scaleY = s
                translationX = (if (fromRight) -1f else 1f) * size.width * 0.04f * p
                alpha = 1f - 0.2f * p
            }
            .clip(RoundedCornerShape((28 * progress.value).dp)),
    ) { content() }
}
