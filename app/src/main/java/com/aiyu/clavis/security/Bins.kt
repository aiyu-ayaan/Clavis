package com.aiyu.clavis.security

import android.content.Context
import com.aiyu.clavis.core.card.BinInfo
import com.aiyu.clavis.core.card.BinLookup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The bundled BIN table, loaded off the main thread on first use (3.4 MB). Offline only. */
@Singleton
class Bins @Inject constructor(@ApplicationContext private val context: Context) {
    private val table by lazy { runCatching { BinLookup(context.assets.open("bins.bin").use { it.readBytes() }) }.getOrNull() }

    suspend fun lookup(digits: String): BinInfo? = withContext(Dispatchers.IO) { table?.lookup(digits) }
}
