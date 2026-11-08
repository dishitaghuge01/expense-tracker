package dev.expensetracker.prototype.data.ledger

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.expensetracker.model.ReportingDates
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

internal fun requireMerchantKey(value: String) {
    requireLabel(value, 80)
    require(value == value.lowercase(Locale.ROOT)) { "INVALID_MERCHANT_KEY" }
}

@Entity(
    tableName = "merchant_rule",
    foreignKeys = [
        ForeignKey(entity = AccountAliasEntity::class, parentColumns = ["id"], childColumns = ["accountId"]),
        ForeignKey(entity = PersonalCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"]),
    ],
    indices = [Index(value = ["merchantKey", "accountId"], unique = true), Index("accountId"), Index("categoryId")],
)
data class MerchantRuleEntity(
    @PrimaryKey val id: UUID,
    val merchantKey: String,
    val accountId: UUID?,
    val categoryId: UUID,
    val updatedAt: Instant,
) {
    init { requireMerchantKey(merchantKey) }
}

@Entity(
    tableName = "sharing_rule",
    foreignKeys = [ForeignKey(entity = AccountAliasEntity::class, parentColumns = ["id"], childColumns = ["accountId"])],
    indices = [Index(value = ["householdId", "enabled"]), Index("accountId")],
)
data class SharingRuleEntity(
    @PrimaryKey val id: UUID,
    val householdId: UUID,
    val merchantKey: String?,
    val accountId: UUID?,
    val householdCategoryId: UUID,
    @ColumnInfo(defaultValue = "0") val enabled: Boolean = false,
    val confirmedAt: Instant,
) {
    init {
        require(merchantKey != null || accountId != null) { "MISSING_MATCH_KEY" }
        merchantKey?.let(::requireMerchantKey)
    }
}

@Entity(tableName = "correction_undo", indices = [Index("expiresAt")])
data class CorrectionUndoEntity(
    @PrimaryKey val operationId: UUID,
    val payload: CorrectionUndoPayload,
    val createdAt: Instant,
    val expiresAt: Instant = createdAt.plus(Duration.ofHours(24)),
) {
    init {
        val lifetime = Duration.between(createdAt, expiresAt)
        require(!lifetime.isNegative && !lifetime.isZero && lifetime <= Duration.ofHours(24)) { "INVALID_UNDO_LIFETIME" }
    }
}

@Entity(
    tableName = "coverage_check",
    foreignKeys = [ForeignKey(entity = AccountAliasEntity::class, parentColumns = ["id"], childColumns = ["accountId"])],
    indices = [Index("accountId")],
)
data class CoverageCheckEntity(
    @PrimaryKey val id: UUID,
    val accountId: UUID,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val statementDigest: String,
    val rowCount: Int,
    val matchedCount: Int,
    val newCount: Int,
    val unresolvedCount: Int,
    val confirmedAt: Instant? = null,
) {
    init {
        ReportingDates.parseDate(fromDate.toString())
        ReportingDates.parseDate(toDate.toString())
        require(!fromDate.isAfter(toDate)) { "INVALID_COVERAGE_RANGE" }
        requireDigest(statementDigest)
        require(rowCount in 0..10_000 && matchedCount >= 0 && newCount >= 0 && unresolvedCount >= 0 &&
            matchedCount.toLong() + newCount + unresolvedCount == rowCount.toLong()) { "INVALID_COVERAGE_COUNTS" }
    }
}
