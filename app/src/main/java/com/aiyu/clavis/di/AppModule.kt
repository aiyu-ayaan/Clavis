package com.aiyu.clavis.di

import android.content.Context
import com.aiyu.clavis.core.crypto.Vault
import com.aiyu.clavis.core.data.CardRepository
import com.aiyu.clavis.core.data.ClavisDatabase
import com.aiyu.clavis.core.settings.SettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.aiyu.clavis.core.model.Card
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/** :core stays DI-free; its objects are built here. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun vault(@ApplicationContext context: Context) = Vault(context)

    @Provides @Singleton
    fun database(@ApplicationContext context: Context) = ClavisDatabase.create(context)

    @Provides @Singleton
    fun cards(db: ClavisDatabase, vault: Vault) = CardRepository(db.cards(), vault)

    @Provides @Singleton
    fun settings(@ApplicationContext context: Context) = SettingsStore(context)

    /** One decrypted list for every screen, so a detail screen has its card on the first frame (shared transitions). */
    @Provides @Singleton
    fun cardList(repo: CardRepository, @AppScope scope: CoroutineScope) = CardList(repo.cards.stateIn(scope, SharingStarted.Eagerly, null))

    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}

/** Decrypted cards while unlocked, empty while locked, null before the first decrypt. */
class CardList(val cards: StateFlow<List<Card>?>)
