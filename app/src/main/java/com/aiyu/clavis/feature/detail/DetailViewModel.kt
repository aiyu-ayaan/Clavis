package com.aiyu.clavis.feature.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.clavis.core.card.formatPan
import com.aiyu.clavis.core.data.CardRepository
import com.aiyu.clavis.core.model.Card
import com.aiyu.clavis.di.CardList
import com.aiyu.clavis.core.settings.Settings
import com.aiyu.clavis.core.settings.SettingsStore
import com.aiyu.clavis.security.AuthGate
import com.aiyu.clavis.security.SecureClipboard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class CopyField { NUMBER, NUMBER_SPACED, EXPIRY, EXPIRY_LONG, HOLDER, CVV, ALL }

@HiltViewModel
class DetailViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repo: CardRepository,
    list: CardList,
    private val gate: AuthGate,
    private val clipboard: SecureClipboard,
    settingsStore: SettingsStore,
) : ViewModel() {
    val id: String = checkNotNull(saved["id"])
    val card = list.cards.map { cards -> cards?.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, list.cards.value?.find { it.id == id })
    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    var revealed by mutableStateOf(false)
        private set
    var cvv by mutableStateOf<String?>(null)
        private set

    suspend fun requireFresh() = gate.requireFresh()

    /** Full number and CVV for 20 s, after a fresh auth. */
    suspend fun reveal(): Boolean {
        if (!gate.requireFresh()) return false
        cvv = if (card.value?.hasCvv == true) repo.cvv(id) else null
        revealed = true
        return true
    }

    fun hide() {
        revealed = false
        cvv = null
    }

    suspend fun loadCvv(): String? = if (gate.requireFresh()) repo.cvv(id) else null

    /** Copies [field]; number, CVV and "all" need a fresh auth. Returns the clear delay, or null if not copied. */
    suspend fun copy(field: CopyField, allText: (Card) -> String = { "" }): Int? {
        val c = card.value ?: return null
        val r = c.record
        if (field in setOf(CopyField.NUMBER, CopyField.NUMBER_SPACED, CopyField.CVV, CopyField.ALL) && !gate.requireFresh()) return null
        val value = when (field) {
            CopyField.NUMBER -> r.pan
            CopyField.NUMBER_SPACED -> formatPan(r.pan, r.network)
            CopyField.EXPIRY -> r.expiry.short
            CopyField.EXPIRY_LONG -> r.expiry.long
            CopyField.HOLDER -> r.holderName
            CopyField.CVV -> repo.cvv(id) ?: return null
            CopyField.ALL -> allText(c)
        }
        val delay = settings.value.clipboardClearSec
        clipboard.copy(value, delay)
        return delay
    }

    fun clearClipboard() = clipboard.clear()

    suspend fun delete(): Boolean {
        if (!gate.requireFresh()) return false
        repo.delete(id)
        return true
    }

    override fun onCleared() = hide()
}
