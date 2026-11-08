package dev.expensetracker.prototype.data.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class ObservationCommit(val inserted: Boolean, val transactionId: UUID?)

@Dao
abstract class LocalLedgerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAccount(account: AccountAliasEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertCategory(category: PersonalCategoryEntity)

    @Query("SELECT * FROM personal_category WHERE archivedAt IS NULL ORDER BY name COLLATE NOCASE, id")
    abstract fun observeActiveCategories(): Flow<List<PersonalCategoryEntity>>

    @Query("SELECT * FROM personal_category WHERE id = :id")
    abstract suspend fun category(id: UUID): PersonalCategoryEntity?

    @Query("UPDATE personal_category SET name = :name WHERE id = :id")
    protected abstract suspend fun renameCategoryRow(id: UUID, name: String): Int

    suspend fun renameCategory(id: UUID, name: String): Int {
        requireLabel(name, 80)
        return renameCategoryRow(id, name)
    }

    @Query("UPDATE personal_category SET archivedAt = :at WHERE id = :id AND archivedAt IS NULL")
    abstract suspend fun archiveCategory(id: UUID, at: Instant): Int

    @Upsert
    protected abstract suspend fun upsertBudgetRow(budget: PersonalBudgetEntity)

    @Transaction
    open suspend fun setBudget(budget: PersonalBudgetEntity) {
        val category = category(budget.categoryId)
        require(category != null && category.archivedAt == null) { "CATEGORY_UNAVAILABLE" }
        upsertBudgetRow(budget)
    }

    @Query("SELECT * FROM personal_budget WHERE month = :month ORDER BY categoryId")
    abstract fun observeBudgets(month: YearMonth): Flow<List<PersonalBudgetEntity>>

    @Query("SELECT * FROM local_transaction WHERE id = :id")
    abstract suspend fun transaction(id: UUID): LocalTransactionEntity?

    @Query("SELECT * FROM local_transaction WHERE reportDate BETWEEN :from AND :to ORDER BY reportDate DESC, id")
    abstract fun observeTransactions(from: LocalDate, to: LocalDate): Flow<List<LocalTransactionEntity>>

    @Query("SELECT * FROM capture_observation WHERE fingerprint = :fingerprint")
    abstract suspend fun observation(fingerprint: String): CaptureObservationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertTransactionRow(record: LocalTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertObservationRow(observation: CaptureObservationEntity)

    /** For a normalized detected event or approved statement row, never a blank-entry UI. */
    @Transaction
    open suspend fun recordDetected(record: LocalTransactionEntity, observation: CaptureObservationEntity): ObservationCommit {
        require(record.sharedId == null && record.shareVersion == null && record.shareState == "private") {
            "NEW_RECORD_MUST_BE_PRIVATE"
        }
        require(observation.transactionId == record.id && observation.outcome == ObservationOutcome.CAPTURED) {
            "OBSERVATION_RECORD_MISMATCH"
        }
        this.observation(observation.fingerprint)?.let { return ObservationCommit(false, it.transactionId) }
        insertTransactionRow(record)
        insertObservationRow(observation)
        return ObservationCommit(true, record.id)
    }

    /** Duplicate evidence must not overwrite the record's manual category/merchant corrections. */
    @Transaction
    open suspend fun attachDuplicate(observation: CaptureObservationEntity): ObservationCommit {
        require(observation.transactionId != null && observation.outcome == ObservationOutcome.DUPLICATE) {
            "INVALID_DUPLICATE_OBSERVATION"
        }
        this.observation(observation.fingerprint)?.let { return ObservationCommit(false, it.transactionId) }
        insertObservationRow(observation)
        return ObservationCommit(true, observation.transactionId)
    }

    @Transaction
    open suspend fun recordUnsupported(observation: CaptureObservationEntity): Boolean {
        require(observation.transactionId == null && observation.outcome in setOf(ObservationOutcome.UNSUPPORTED, ObservationOutcome.IGNORED)) {
            "INVALID_UNSUPPORTED_OBSERVATION"
        }
        if (this.observation(observation.fingerprint) != null) return false
        insertObservationRow(observation)
        return true
    }

    /** Category correction is not existence confirmation, and never changes an existing cloud share. */
    @Query("""UPDATE local_transaction SET categoryId = :categoryId, localVersion = localVersion + 1, updatedAt = :at
        WHERE id = :id AND localVersion = :expectedVersion AND localVersion < 9223372036854775807""")
    abstract suspend fun changeCategory(id: UUID, categoryId: UUID?, expectedVersion: Long, at: Instant): Int

    @Query("""UPDATE local_transaction SET status = 'excluded', localVersion = localVersion + 1, updatedAt = :at
        WHERE id = :id AND localVersion = :expectedVersion AND localVersion < 9223372036854775807""")
    abstract suspend fun exclude(id: UUID, expectedVersion: Long, at: Instant): Int
}
