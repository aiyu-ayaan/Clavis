package com.aiyu.clavis.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.aiyu.clavis.core.crypto.Field
import com.aiyu.clavis.core.crypto.Vault
import com.aiyu.clavis.core.model.Card
import com.aiyu.clavis.core.model.CardRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

/** One row per card. Only ids, timestamps and order are plaintext; everything about the card is ciphertext. */
@Entity(tableName = "cards")
class CardEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sortKey: Int,
    val payload: ByteArray,
    val cvvPayload: ByteArray?,
)

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY sortKey, createdAt")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: String): CardEntity?

    @Query("SELECT COALESCE(MAX(sortKey), -1) + 1 FROM cards")
    suspend fun nextSortKey(): Int

    @Upsert
    suspend fun upsert(entity: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cards")
    suspend fun deleteAll()
}

@Database(entities = [CardEntity::class], version = 1)
abstract class ClavisDatabase : RoomDatabase() {
    abstract fun cards(): CardDao

    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, ClavisDatabase::class.java, "clavis.db").build()
    }
}

class AuthRequiredException : Exception("Fresh authentication required")

/** Ciphertext in, [Card] out. Decrypts only while the vault is unlocked. */
class CardRepository(private val dao: CardDao, private val vault: Vault) {
    private val json = Json { ignoreUnknownKeys = true }

    /** null while locked, so screens never mistake "locked" for "no cards". */
    val cards: Flow<List<Card>?> = combine(dao.observeAll(), vault.state) { rows, _ ->
        val cipher = vault.cipher ?: return@combine null
        rows.mapNotNull { row ->
            runCatching {
                Card(row.id, json.decodeFromString<CardRecord>(cipher.open(row.id, Field.PAYLOAD, row.payload).decodeToString()), row.cvvPayload != null, row.sortKey)
            }.getOrNull()
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Saves [record]. [cvv]: null keeps the stored CVV, "" removes it, digits replace it.
     * Returns the card id.
     */
    suspend fun save(id: String?, record: CardRecord, cvv: String?): String = withContext(Dispatchers.Default) {
        val cipher = vault.cipher ?: throw AuthRequiredException()
        val existing = id?.let { dao.get(it) }
        val cardId = existing?.id ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val cvvPayload = when {
            cvv == null -> existing?.cvvPayload
            cvv.isEmpty() -> null
            else -> cipher.seal(cardId, Field.CVV, cvv.toByteArray())
        }
        dao.upsert(
            CardEntity(
                id = cardId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                sortKey = existing?.sortKey ?: dao.nextSortKey(),
                payload = cipher.seal(cardId, Field.PAYLOAD, json.encodeToString(record).toByteArray()),
                cvvPayload = cvvPayload,
            ),
        )
        cardId
    }

    /** The CVV, gated twice: cryptographically by the CVV subkey and in software by a fresh auth. */
    suspend fun cvv(id: String): String? = withContext(Dispatchers.Default) {
        if (!vault.isFresh()) throw AuthRequiredException()
        val cipher = vault.cipher ?: throw AuthRequiredException()
        dao.get(id)?.cvvPayload?.let { cipher.open(id, Field.CVV, it).decodeToString() }
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()
}
