package dev.expensetracker.prototype.data.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.time.Instant
import java.util.UUID

/** Inactive local storage only: no application of rules, undo, or sharing side effects. */
@Dao
abstract class LocalRulesDao {
    @Query("SELECT COUNT(*) FROM personal_category WHERE id = :id AND archivedAt IS NULL")
    protected abstract suspend fun activeCategoryCount(id: UUID): Int

    @Upsert
    protected abstract suspend fun putMerchantRuleRow(rule: MerchantRuleEntity)

    /** Does not modify any past transaction or existing shared entry. */
    @Transaction
    open suspend fun putMerchantRule(rule: MerchantRuleEntity) {
        require(activeCategoryCount(rule.categoryId) == 1) { "CATEGORY_UNAVAILABLE" }
        putMerchantRuleRow(rule)
    }

    @Query("""SELECT merchant_rule.* FROM merchant_rule
        INNER JOIN personal_category ON personal_category.id = merchant_rule.categoryId
        WHERE merchantKey = :merchantKey AND personal_category.archivedAt IS NULL
        AND (accountId = :accountId OR accountId IS NULL)
        ORDER BY CASE WHEN accountId IS NULL THEN 1 ELSE 0 END LIMIT 1""")
    protected abstract suspend fun merchantRuleRow(merchantKey: String, accountId: UUID?): MerchantRuleEntity?

    suspend fun merchantRule(merchantKey: String, accountId: UUID?): MerchantRuleEntity? {
        requireMerchantKey(merchantKey)
        return merchantRuleRow(merchantKey, accountId)
    }

    @Upsert
    abstract suspend fun putSharingRule(rule: SharingRuleEntity)

    /** Returns all enabled conflicts without selecting a category or sharing anything. */
    @Query("""SELECT * FROM sharing_rule WHERE householdId = :householdId AND enabled = 1
        AND (merchantKey IS NULL OR merchantKey = :merchantKey)
        AND (accountId IS NULL OR accountId = :accountId) ORDER BY id""")
    protected abstract suspend fun matchingSharingRulesRow(
        householdId: UUID, merchantKey: String?, accountId: UUID?,
    ): List<SharingRuleEntity>

    suspend fun matchingSharingRules(householdId: UUID, merchantKey: String?, accountId: UUID?): List<SharingRuleEntity> {
        merchantKey?.let(::requireMerchantKey)
        return matchingSharingRulesRow(householdId, merchantKey, accountId)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCoverage(check: CoverageCheckEntity)

    suspend fun putCoverage(check: CoverageCheckEntity) {
        require(check.confirmedAt == null) { "COVERAGE_REQUIRES_EXPLICIT_CONFIRMATION" }
        insertCoverage(check)
    }

    @Query("SELECT * FROM coverage_check WHERE id = :id")
    abstract suspend fun coverage(id: UUID): CoverageCheckEntity?

    @Query("""UPDATE coverage_check SET confirmedAt = :at WHERE id = :id
        AND statementDigest = :statementDigest AND unresolvedCount = 0 AND confirmedAt IS NULL""")
    protected abstract suspend fun confirmCoverageRow(id: UUID, statementDigest: String, at: Instant): Int

    /** Call only following explicit user confirmation of this account, period and digest. */
    suspend fun confirmCoverage(id: UUID, statementDigest: String, at: Instant): Int {
        requireDigest(statementDigest)
        return confirmCoverageRow(id, statementDigest, at)
    }

    /** Operation IDs identify immutable inverse changes; repeated saves must fail. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun putUndo(undo: CorrectionUndoEntity)

    @Query("SELECT * FROM correction_undo WHERE operationId = :operationId AND expiresAt > :now")
    abstract suspend fun undo(operationId: UUID, now: Instant): CorrectionUndoEntity?

    @Query("DELETE FROM correction_undo WHERE expiresAt <= :now")
    abstract suspend fun purgeExpiredUndo(now: Instant): Int
}
