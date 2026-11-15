package com.aiyu.clavis.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.aiyu.clavis.core.crypto.VaultState
import com.aiyu.clavis.feature.addcard.AddCardScreen
import com.aiyu.clavis.feature.detail.CardDetailScreen
import com.aiyu.clavis.feature.home.HomeScreen
import com.aiyu.clavis.feature.landing.LandingScreen
import com.aiyu.clavis.feature.lock.LockScreen
import com.aiyu.clavis.feature.scan.ScanScreen
import com.aiyu.clavis.feature.settings.ChangePinScreen
import com.aiyu.clavis.feature.settings.SettingsScreen
import com.aiyu.clavis.feature.setup.SetupScreen
import com.aiyu.clavis.ui.components.LocalNavAnimatedScope
import com.aiyu.clavis.ui.components.LocalSharedTransitionScope
import com.aiyu.clavis.ui.components.PredictiveBack
import com.aiyu.clavis.ui.components.SharedKeys
import com.aiyu.clavis.ui.components.sharedBoundsOrSelf
import kotlinx.serialization.Serializable

object Routes {
    @Serializable data object Landing
    @Serializable data object Setup
    @Serializable data object Lock
    @Serializable data object Home
    @Serializable data class AddCard(val id: String? = null)
    @Serializable data object Scan
    @Serializable data class Detail(val id: String)
    @Serializable data object Settings
    @Serializable data object ChangePin
    @Serializable data object ResetPin
}

private fun NavBackStackEntry.isAny(vararg routes: kotlin.reflect.KClass<*>) = routes.any { destination.hasRoute(it) }

/**
 * One shared-transition scope around the whole graph: the brand mark flies landing -> setup -> lock -> home,
 * cards grow into their detail (container transform), the add FAB grows into the add form.
 * Screens around a shared element only fade, so the moving element carries the motion.
 */
@Composable
fun ClavisNavHost(session: SessionViewModel, nav: NavHostController = rememberNavController()) {
    val state by session.state.collectAsStateWithLifecycle()
    val start: Any = remember { if (state == VaultState.NOT_SET_UP) Routes.Landing else Routes.Lock }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // Lock clears the back stack (03-ARCHITECTURE.md); erase returns to the landing.
    LaunchedEffect(state) {
        val current = nav.currentBackStackEntry ?: return@LaunchedEffect
        when (state) {
            VaultState.LOCKED -> if (!current.isAny(Routes.Lock::class)) nav.navigate(Routes.Lock) { popUpTo(nav.graph.id) { inclusive = true } }
            VaultState.NOT_SET_UP -> if (!current.isAny(Routes.Landing::class, Routes.Setup::class)) nav.navigate(Routes.Landing) { popUpTo(nav.graph.id) { inclusive = true } }
            VaultState.UNLOCKED -> Unit
        }
    }

    val fadeOnly: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { fadeIn(effects) }
    val fadeOutOnly: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { fadeOut(effects) }

    Surface(Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = nav,
                    startDestination = start,
                    // Forward slides in from the end, back from the start (navigation-direction).
                    enterTransition = { slideInHorizontally(spatial) { it / 4 } + fadeIn(effects) },
                    exitTransition = { slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects) },
                    popEnterTransition = { slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects) },
                    popExitTransition = { slideOutHorizontally(spatial) { it / 4 } + fadeOut(effects) },
                ) {
                    composable<Routes.Landing>(exitTransition = { fadeOut(tween(350)) }, popEnterTransition = fadeOnly) {
                        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                            LandingScreen(onGetStarted = { nav.navigate(Routes.Setup) })
                        }
                    }
                    composable<Routes.Setup>(enterTransition = fadeOnly, popExitTransition = fadeOutOnly, exitTransition = fadeOutOnly) {
                        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                            SetupScreen(
                                onBack = { nav.popBackStack() },
                                onDone = { nav.navigate(Routes.Home) { popUpTo(nav.graph.id) { inclusive = true } } },
                            )
                        }
                    }
                    composable<Routes.Lock>(enterTransition = fadeOnly, exitTransition = fadeOutOnly) {
                        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                            LockScreen(
                                session,
                                onUnlocked = { nav.navigate(Routes.Home) { popUpTo(nav.graph.id) { inclusive = true } } },
                                onRecovered = { nav.navigate(Routes.ResetPin) { popUpTo(nav.graph.id) { inclusive = true } } },
                            )
                        }
                    }
                    composable<Routes.Home>(
                        enterTransition = { if (initialState.isAny(Routes.Lock::class, Routes.Setup::class)) fadeIn(tween(450, 100)) + scaleIn(tween(450, 100), initialScale = 0.96f) else fadeIn(effects) },
                        exitTransition = { if (targetState.isAny(Routes.Detail::class, Routes.AddCard::class)) fadeOut(effects) else slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects) },
                        popEnterTransition = { if (initialState.isAny(Routes.Detail::class, Routes.AddCard::class)) fadeIn(effects) else slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects) },
                    ) {
                        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                            HomeScreen(
                                onOpenCard = { nav.navigate(Routes.Detail(it)) },
                                onAddManual = { nav.navigate(Routes.AddCard()) },
                                onScan = { nav.navigate(Routes.Scan) },
                                onSettings = { nav.navigate(Routes.Settings) },
                                onLock = session::lock,
                            )
                        }
                    }
                    composable<Routes.Detail>(enterTransition = fadeOnly, popExitTransition = fadeOutOnly, exitTransition = { if (targetState.isAny(Routes.AddCard::class)) fadeOut(effects) else slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects) }) {
                        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                            PredictiveBack(onBack = { nav.popBackStack() }) {
                                CardDetailScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.AddCard(id = it)) })
                            }
                        }
                    }
                    composable<Routes.AddCard>(enterTransition = fadeOnly, popExitTransition = fadeOutOnly) { entry ->
                        val route = entry.toRoute<Routes.AddCard>()
                        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                            PredictiveBack(onBack = { nav.popBackStack() }) {
                                AddCardScreen(
                                    onBack = { nav.popBackStack() },
                                    onSaved = { id ->
                                        if (route.id != null) nav.popBackStack()
                                        else nav.navigate(Routes.Detail(id)) { popUpTo(Routes.Home) }
                                    },
                                    previewModifier = Modifier.sharedBoundsOrSelf(
                                        if (route.id != null) SharedKeys.card(route.id) else SharedKeys.ADD_CARD,
                                        MaterialTheme.shapes.extraLarge,
                                    ),
                                )
                            }
                        }
                    }
                    composable<Routes.Scan>(enterTransition = { fadeIn(effects) + scaleIn(initialScale = 0.92f) }, popExitTransition = { fadeOut(effects) + scaleOut(targetScale = 0.92f) }) {
                        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                            ScanScreen(
                                onBack = { nav.popBackStack() },
                                onResult = { nav.navigate(Routes.AddCard()) { popUpTo(Routes.Home) } },
                                onManual = { nav.navigate(Routes.AddCard()) { popUpTo(Routes.Home) } },
                            )
                        }
                    }
                    composable<Routes.Settings> {
                        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                            PredictiveBack(onBack = { nav.popBackStack() }) {
                                SettingsScreen(onBack = { nav.popBackStack() }, onChangePin = { nav.navigate(Routes.ChangePin) })
                            }
                        }
                    }
                    composable<Routes.ResetPin>(enterTransition = fadeOnly) {
                        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                            ChangePinScreen(
                                onBack = {},
                                onDone = { nav.navigate(Routes.Home) { popUpTo(nav.graph.id) { inclusive = true } } },
                                reset = session::resetPin,
                            )
                        }
                    }
                    composable<Routes.ChangePin> {
                        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                            ChangePinScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
        FreshAuthHost(session)
        com.aiyu.clavis.update.UpdateHost(unlocked = state == VaultState.UNLOCKED)
    }
}

