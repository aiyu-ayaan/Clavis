package com.aiyu.clavis.feature.landing

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.components.ClavisMark
import com.aiyu.clavis.ui.components.SharedKeys
import com.aiyu.clavis.ui.components.sharedElementOrSelf
import com.aiyu.clavis.ui.theme.ClavisMotion
import com.aiyu.clavis.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class Page(@StringRes val title: Int, @StringRes val body: Int, val icon: ImageVector)

private val pages = listOf(
    Page(R.string.intro_title_1, R.string.intro_body_1, Icons.Rounded.CreditCard),
    Page(R.string.intro_title_2, R.string.intro_body_2, Icons.Rounded.Lock),
    Page(R.string.intro_title_3, R.string.intro_body_3, Icons.Rounded.ContentCopy),
)

/**
 * Full-screen landing (06-UX-DESIGN.md screen 2): morphing brand mark with an orbit, pager story, Next/Get started.
 * The mark is a shared element: it flies into the setup screen's header, so the hand-off reads as one motion.
 */
@Composable
fun LandingScreen(onGetStarted: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val last = pager.currentPage == pages.lastIndex
    val appName = stringResource(R.string.app_name)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = 0.6f), colors.surface, colors.surface))),
    ) {
        val screenHeight = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                appName.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 6.sp),
                color = colors.primary,
                modifier = Modifier.padding(top = 24.dp).sharedElementOrSelf(SharedKeys.TITLE),
            )
            // Hero: fixed share of the window (min 220 dp) so landscape and large text scroll instead of clipping.
            BoxWithConstraints(
                Modifier.fillMaxWidth().height((screenHeight * 0.42f).coerceAtLeast(220.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val hero = minOf(maxWidth, maxHeight) * 0.56f
                Orbit(Modifier.size(hero * 1.6f))
                Box(contentAlignment = Alignment.Center) {
                    ClavisMark(
                        Modifier
                            .size(hero)
                            .sharedElementOrSelf(SharedKeys.MARK)
                            .semantics { contentDescription = appName },
                        icon = pages[pager.currentPage].icon,
                    )
                }
            }
            Text(
                stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(20.dp))
            HorizontalPager(pager, Modifier.fillMaxWidth().heightIn(min = 104.dp)) { page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(pages[page].title), style = MaterialTheme.typography.titleLarge, color = colors.primary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(pages[page].body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 360.dp),
                    )
                }
            }
            PageDots(pages.size, pager.currentPage)
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { if (last) onGetStarted() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                AnimatedContent(last, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.9f)) }, label = "cta") { isLast ->
                    Text(stringResource(if (isLast) R.string.action_get_started else R.string.action_next), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
            TextButton(onClick = onGetStarted, modifier = Modifier.alpha(if (last) 0f else 1f).padding(bottom = 8.dp), enabled = !last) {
                Text(stringResource(R.string.action_skip))
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 28.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}

/** Small satellites circling the mark: tertiary and primary dots on a faint ring. Static with reduced motion. */
@Composable
private fun Orbit(modifier: Modifier) {
    val reduced = rememberReducedMotion()
    val angle = if (reduced) null else rememberInfiniteTransition(label = "orbit").animateFloat(
        0f, 360f, infiniteRepeatable(tween(ClavisMotion.ORBIT_MILLIS, easing = LinearEasing)), label = "a",
    )
    val ring = MaterialTheme.colorScheme.outlineVariant
    val dots = listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    Canvas(modifier) {
        val r = size.minDimension / 2 - 8.dp.toPx()
        drawCircle(ring.copy(alpha = 0.5f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        val base = angle?.value ?: 30f
        dots.forEachIndexed { i, c ->
            val a = (base + i * 120f) * PI.toFloat() / 180f
            drawCircle(c, radius = (7 - i * 1.5f).dp.toPx(), center = Offset(center.x + r * cos(a), center.y + r * sin(a)))
        }
    }
}
