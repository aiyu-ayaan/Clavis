package com.aiyu.clavis.feature.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiyu.clavis.core.card.CardGrouping
import com.aiyu.clavis.core.card.CardLayout
import com.aiyu.clavis.ui.R

/** Toolbar "View options": how cards are drawn (cards, list, wallet) and how they are grouped. */
@Composable
fun ViewOptionsSheet(
    layout: CardLayout,
    grouping: CardGrouping,
    onLayout: (CardLayout) -> Unit,
    onGrouping: (CardGrouping) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            SheetHeading(R.string.view_layout)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CardLayout.entries.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = l == layout,
                        onClick = { onLayout(l) },
                        shape = SegmentedButtonDefaults.itemShape(i, CardLayout.entries.size),
                        icon = { SegmentedButtonDefaults.Icon(l == layout) { Icon(l.icon, contentDescription = null, Modifier.size(SegmentedButtonDefaults.IconSize)) } },
                        label = { Text(stringResource(l.labelRes)) },
                    )
                }
            }
            AnimatedVisibility(layout == CardLayout.WALLET) {
                Text(
                    stringResource(R.string.view_wallet_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            SheetHeading(R.string.view_group_by)
            Spacer(Modifier.height(4.dp))
            Column(Modifier.selectableGroup()) {
                CardGrouping.entries.forEach { g ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(MaterialTheme.shapes.large)
                            .selectable(selected = g == grouping, onClick = { onGrouping(g) }, role = Role.RadioButton)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = g == grouping, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(g.labelRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeading(@StringRes text: Int) =
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })

private val CardLayout.icon: ImageVector
    get() = when (this) {
        CardLayout.CARDS -> Icons.Rounded.ViewAgenda
        CardLayout.LIST -> Icons.AutoMirrored.Rounded.List
        CardLayout.WALLET -> Icons.Rounded.AccountBalanceWallet
    }

private val CardLayout.labelRes: Int
    get() = when (this) {
        CardLayout.CARDS -> R.string.view_layout_cards
        CardLayout.LIST -> R.string.view_layout_list
        CardLayout.WALLET -> R.string.view_layout_wallet
    }

private val CardGrouping.labelRes: Int
    get() = when (this) {
        CardGrouping.NONE -> R.string.view_group_none
        CardGrouping.NAME -> R.string.view_group_name
        CardGrouping.NETWORK -> R.string.view_group_network
        CardGrouping.BANK -> R.string.view_group_bank
        CardGrouping.TYPE -> R.string.view_group_type
        CardGrouping.EXPIRY -> R.string.view_group_expiry
    }
