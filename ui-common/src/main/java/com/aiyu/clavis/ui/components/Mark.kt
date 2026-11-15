package com.aiyu.clavis.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.theme.ClavisMotion
import com.aiyu.clavis.ui.theme.rememberReducedMotion

/** Hero sequence for the landing and the brand mark: rounded, "sealed" shapes. */
val HeroShapes: List<RoundedPolygon>
    get() = listOf(
        MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny,
        MaterialShapes.Gem, MaterialShapes.Puffy, MaterialShapes.SoftBurst,
    )

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Morphs through [shapes] while slowly rotating. Each segment morphs for 60% then rests, so it reads as
 * breathing, not spinning. Static first shape when [animate] is false or animations are off.
 */
@Composable
fun MorphingShape(
    brush: Brush,
    modifier: Modifier = Modifier,
    shapes: List<RoundedPolygon> = HeroShapes,
    animate: Boolean = true,
    segmentMillis: Int = ClavisMotion.MORPH_MILLIS,
    rotationMillis: Int = ClavisMotion.ORBIT_MILLIS,
) {
    val morphs = remember(shapes) { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val live = animate && !rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "morph")
    val t = if (live) transition.animateFloat(
        0f, shapes.size.toFloat(),
        infiniteRepeatable(tween(segmentMillis * shapes.size, easing = LinearEasing)), label = "t",
    ) else null
    val rotation = if (live) transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(rotationMillis, easing = LinearEasing), RepeatMode.Restart), label = "rot",
    ) else null

    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Canvas(modifier) {
        val value = t?.value ?: 0f
        val index = value.toInt() % morphs.size
        val local = value - value.toInt()
        val progress = Emphasized.transform((local / 0.6f).coerceAtMost(1f))
        path.rewind()
        morphs[index].toPath(progress, path)
        matrix.reset()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        rotate(rotation?.value ?: 0f, pivot = Offset(size.width / 2, size.height / 2)) {
            drawPath(path, brush)
        }
    }
}

/** Brand fill: primary into its tonal partner. Works in light, dark and dynamic schemes. */
@Composable
fun heroBrush(): Brush {
    val c = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(c.primary, c.tertiary))
}

/**
 * The Clavis mark: a morphing seal with the brand glyph (card in its sleeve) or, on auth screens, a state icon.
 * The same composable is used at every size so the shared-element transition between landing, setup, lock and
 * home reads as one object.
 */
@Composable
fun ClavisMark(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    icon: ImageVector? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        MorphingShape(heroBrush(), Modifier.fillMaxSize(), animate = animate)
        val tint = MaterialTheme.colorScheme.onPrimary
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize(0.42f))
        else Icon(painterResource(R.drawable.clavis_glyph), contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize(0.6f))
    }
}
