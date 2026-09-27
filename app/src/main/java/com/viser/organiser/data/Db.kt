package com.viser.organiser.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND type != 'todo' ORDER BY pinned DESC, createdAt DESC")
    fun savedItems(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND type = 'todo' ORDER BY CASE WHEN remindAt IS NULL THEN 1 ELSE 0 END, remindAt ASC, createdAt DESC")
    fun todos(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: String): Flow<Item?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): Item?

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND done = 0 AND remindAt IS NOT NULL")
    suspend fun withReminders(): List<Item>

    @Query("SELECT * FROM items")
    suspend fun all(): List<Item>

    @Upsert
    suspend fun upsert(item: Item)

    @Upsert
    suspend fun upsertAll(items: List<Item>)
}

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections ORDER BY sort, name")
    fun observe(): Flow<List<Section>>

    @Query("SELECT * FROM sections ORDER BY sort, name")
    suspend fun all(): List<Section>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<Section>)

    @Upsert
    suspend fun upsert(s: Section)

    @Query("DELETE FROM sections WHERE name = :name")
    suspend fun delete(name: String)
}

@Dao
interface TxnDao {
    @Query("SELECT * FROM txns WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt DESC")
    fun between(from: Long, to: Long): Flow<List<Txn>>

    @Query("SELECT * FROM txns WHERE deletedAt IS NULL AND status = 'pending' ORDER BY occurredAt DESC")
    fun pending(): Flow<List<Txn>>

    @Query("SELECT * FROM txns WHERE deletedAt IS NULL AND status = 'confirmed' AND occurredAt >= :from ORDER BY occurredAt DESC")
    fun confirmedSince(from: Long): Flow<List<Txn>>

    @Query("SELECT * FROM txns WHERE id = :id")
    suspend fun get(id: String): Txn?

    @Query("SELECT * FROM txns WHERE deletedAt IS NULL AND upiRef = :ref AND upiRef != '' LIMIT 1")
    suspend fun byRef(ref: String): Txn?

    @Query("SELECT * FROM txns WHERE deletedAt IS NULL AND amount = :amount AND kind = :kind AND occurredAt BETWEEN :from AND :to")
    suspend fun near(amount: Long, kind: String, from: Long, to: Long): List<Txn>

    @Query("SELECT splitWith FROM txns WHERE deletedAt IS NULL AND splitWith != '' ORDER BY occurredAt DESC LIMIT 200")
    fun recentSplits(): Flow<List<String>>

    @Query("SELECT * FROM txns WHERE deletedAt IS NULL AND status = 'pending' AND occurredAt >= :since ORDER BY occurredAt DESC")
    suspend fun recentPending(since: Long): List<Txn>

    @Query("SELECT COUNT(*) FROM txns WHERE smsHash = :hash")
    suspend fun hashCount(hash: String): Int

    @Query("SELECT * FROM txns")
    suspend fun all(): List<Txn>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: Txn): Long

    @Upsert
    suspend fun upsert(t: Txn)

    @Upsert
    suspend fun upsertAll(list: List<Txn>)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE deletedAt IS NULL ORDER BY createdAt")
    fun observe(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: String): Goal?

    @Query("SELECT * FROM contributions WHERE deletedAt IS NULL ORDER BY at DESC")
    fun contributions(): Flow<List<Contribution>>

    @Query("SELECT COALESCE(SUM(amount),0) FROM contributions WHERE deletedAt IS NULL AND goalId = :goalId")
    suspend fun savedFor(goalId: String): Long

    @Query("SELECT * FROM goals")
    suspend fun allGoals(): List<Goal>

    @Query("SELECT * FROM contributions")
    suspend fun allContributions(): List<Contribution>

    @Upsert
    suspend fun upsert(g: Goal)

    @Upsert
    suspend fun upsertAllGoals(list: List<Goal>)

    @Upsert
    suspend fun upsertContribution(c: Contribution)

    @Upsert
    suspend fun upsertAllContributions(list: List<Contribution>)
}

@Dao
interface RuleDao {
    @Query("SELECT category FROM rules WHERE merchantKey = :key")
    suspend fun categoryFor(key: String): String?

    @Upsert
    suspend fun upsert(r: MerchantRule)

    @Query("SELECT * FROM rules")
    suspend fun all(): List<MerchantRule>

    @Upsert
    suspend fun upsertAll(list: List<MerchantRule>)
}

@Database(
    entities = [Item::class, Section::class, Txn::class, Goal::class, Contribution::class, MerchantRule::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDb : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun sections(): SectionDao
    abstract fun txns(): TxnDao
    abstract fun goals(): GoalDao
    abstract fun rules(): RuleDao

    companion object {
        /** v2: split expenses — who it was split with and the owner's share. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE txns ADD COLUMN splitWith TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE txns ADD COLUMN myShare INTEGER")
            }
        }

        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "organiser.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
