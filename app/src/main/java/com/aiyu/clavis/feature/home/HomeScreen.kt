package com.aiyu.clavis.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiyu.clavis.BuildConfig
import com.aiyu.clavis.core.card.CardGrouping
import com.aiyu.clavis.core.card.CardLayout
import com.aiyu.clavis.core.settings.SettingsStore
import com.aiyu.clavis.di.CardList
import com.aiyu.clavis.core.model.Card
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.components.CardSurface
import com.aiyu.clavis.ui.components.ClavisMark
import com.aiyu.clavis.ui.components.MorphingShape
import com.aiyu.clavis.ui.components.SharedKeys
import com.aiyu.clavis.ui.components.heroBrush
import com.aiyu.clavis.ui.components.isSharedTransitionRunning
import com.aiyu.clavis.ui.components.sharedBoundsOrSelf
import com.aiyu.clavis.ui.components.sharedElementOrSelf
import com.aiyu.clavis.ui.mark
import com.aiyu.clavis.ui.maskedDescription
import com.aiyu.clavis.ui.theme.ClavisMotion
import com.aiyu.clavis.ui.theme.rememberReducedMotion
import com.aiyu.clavis.ui.title
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(list: CardList, private val store: SettingsStore) : ViewModel() {
    /** null while the first decrypt runs, so the empty state does not flash. */
    val cards = list.cards

    /** null until the saved view loads, so the list does not jump from the default layout. */
    val view = store.settings.map { HomeView(it.homeLayout, it.homeGrouping) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setLayout(layout: CardLayout) = viewModelScope.launch { store.update { it.copy(homeLayout = layout) } }
    fun setGrouping(grouping: CardGrouping) = viewModelScope.launch { store.update { it.copy(homeGrouping = grouping) } }
}

data class HomeView(val layout: CardLayout, val grouping: CardGrouping)

/** Home (screen 5): large collapsing bar, wallet of card surfaces, FAB menu (scan / manual). */
@Composable
fun HomeScreen(
    onOpenCard: (String) -> Unit,
    onAddManual: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    var viewOptions by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Hoisted above the null/empty branches so a reload or detail round-trip keeps the scroll position.
    val listState = rememberLazyListState()
    var fabOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.home_title))
                        if (BuildConfig.FLAVOR == "dev") DevBadge(Modifier.padding(start = 12.dp))
                    }
                },
                subtitle = cards?.takeIf { it.isNotEmpty() }?.let { { Text(pluralStringResource(R.plurals.home_card_count, it.size, it.size)) } },
                navigationIcon = {
                    ClavisMark(Modifier.padding(start = 12.dp).size(40.dp).sharedElementOrSelf(SharedKeys.MARK), animate = false)
                },
                actions = {
                    if (cards?.isNotEmpty() == true) {
                        IconButton(onClick = { viewOptions = true }) { Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.home_view_options)) }
                    }
                    IconButton(onClick = onLock) { Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.action_lock)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.home_settings)) }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            AddCardFab(
                visible = !isSharedTransitionRunning() && cards?.isNotEmpty() == true,
                open = fabOpen,
                onToggle = { fabOpen = it },
                onAddManual = { fabOpen = false; onAddManual() },
                onScan = { fabOpen = false; onScan() },
            )
        },
    ) { padding ->
        // Captured once: locking nulls the list while this screen is still animating out.
        val list = cards
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(Modifier.padding(padding), onAddManual, onScan)
            else -> view?.let { v ->
                CardCollection(
                    sections = rememberSections(list, v.grouping),
                    layout = v.layout,
                    listState = listState,
                    bottomPadding = padding.calculateBottomPadding() + 120.dp,
                    onOpenCard = onOpenCard,
                    // Top padding on the list itself, so sticky headers stop under the app bar.
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                )
            }
        }
    }

    val v = view
    if (viewOptions && v != null) {
        ViewOptionsSheet(
            layout = v.layout,
            grouping = v.grouping,
            // A new arrangement starts at the top; the old scroll index points somewhere else in it.
            onLayout = { vm.setLayout(it); scope.launch { listState.scrollToItem(0) } },
            onGrouping = { vm.setGrouping(it); scope.launch { listState.scrollToItem(0) } },
            onDismiss = { viewOptions = false },
        )
    }
}

/** Marks the dev flavor so it is never mistaken for the real vault. */
@Composable
private fun DevBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.dev_badge),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun AddCardFab(visible: Boolean, open: Boolean, onToggle: (Boolean) -> Unit, onAddManual: () -> Unit, onScan: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible,
        enter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.6f),
        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f),
    ) {
        val label = stringResource(R.string.home_add_card)
        val state = stringResource(if (open) R.string.state_expanded else R.string.state_collapsed)
        FloatingActionButtonMenu(
            expanded = open,
            button = {
                ToggleFloatingActionButton(
                    checked = open,
                    onCheckedChange = onToggle,
                    modifier = Modifier
                        .sharedBoundsOrSelf(SharedKeys.ADD_CARD, MaterialTheme.shapes.large)
                        .semantics { stateDescription = state; traversalIndex = -1f },
                ) {
                    Icon(if (open) Icons.Rounded.Close else Icons.Rounded.Add, contentDescription = label)
                }
            },
        ) {
            FloatingActionButtonMenuItem(onClick = onScan, text = { Text(stringResource(R.string.home_scan_card)) }, icon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) })
            FloatingActionButtonMenuItem(onClick = onAddManual, text = { Text(stringResource(R.string.home_enter_manually)) }, icon = { Icon(Icons.Rounded.Edit, contentDescription = null) })
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onAddManual: () -> Unit, onScan: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            MorphingShape(heroBrush(), Modifier.size(180.dp))
            Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onAddManual,
            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight).sharedBoundsOrSelf(SharedKeys.ADD_CARD, MaterialTheme.shapes.large),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_add_card), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            androidx.compose.material3.TextButton(onClick = onScan) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_scan_card))
            }
        }
    }
}
