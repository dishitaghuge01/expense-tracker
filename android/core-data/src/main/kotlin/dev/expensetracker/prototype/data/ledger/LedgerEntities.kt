package dev.expensetracker.prototype.data.ledger

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.expensetracker.model.Inr
import dev.expensetracker.model.ReportingDates
import dev.expensetracker.model.TransactionKind
import dev.expensetracker.model.TransactionStatus
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal fun requireLabel(value: String, max: Int) {
    require(value == value.trim() && value == Normalizer.normalize(value, Normalizer.Form.NFC) &&
        value.codePointCount(0, value.length) in 1..max && value.none { it.isISOControl() }
    ) { "INVALID_LABEL" }
    require(!Regex("[0-9]{9,}").containsMatchIn(value) &&
        !Regex("[A-Za-z0-9._-]+@[A-Za-z][A-Za-z0-9.-]+").containsMatchIn(value)
    ) { "SENSITIVE_LABEL" }
}

internal fun requireCode(value: String) {
    require(Regex("[A-Za-z][A-Za-z0-9_.-]{0,79}").matches(value)) { "INVALID_CODE" }
}

internal fun requireDigest(value: String) {
    require(Regex("[0-9a-f]{64}").matches(value)) { "INVALID_DIGEST" }
}

@Entity(tableName = "account_alias")
data class AccountAliasEntity(
    @PrimaryKey val id: UUID,
    val institutionCode: String,
    val instrumentType: String,
    val maskedSuffix: String?,
    val userLabel: String,
    val state: String,
    val createdAt: Instant,
) {
    init {
        requireCode(institutionCode)
        requireCode(instrumentType)
        requireCode(state)
        requireLabel(userLabel, 40)
        require(maskedSuffix == null || Regex("[0-9]{1,4}").matches(maskedSuffix)) { "INVALID_MASKED_SUFFIX" }
    }
}

@Entity(tableName = "personal_category", indices = [Index("name"), Index("seedCode")])
data class PersonalCategoryEntity(
    @PrimaryKey val id: UUID,
    val seedCode: String?,
    val name: String,
    val archivedAt: Instant? = null,
) {
    init {
        requireLabel(name, 80)
        seedCode?.let(::requireCode)
    }
}

@Entity(
    tableName = "personal_budget", primaryKeys = ["categoryId", "month"],
    foreignKeys = [ForeignKey(entity = PersonalCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"])],
    indices = [Index("month")],
)
data class PersonalBudgetEntity(
    val categoryId: UUID,
    val month: YearMonth,
    val limitPaise: Long,
    val updatedAt: Instant,
) {
    init { Inr.requireAmount(limitPaise) }
}

@Entity(
    tableName = "local_transaction",
    foreignKeys = [
        ForeignKey(entity = AccountAliasEntity::class, parentColumns = ["id"], childColumns = ["accountId"]),
        ForeignKey(entity = PersonalCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"]),
        ForeignKey(entity = LocalTransactionEntity::class, parentColumns = ["id"], childColumns = ["refundOfId"]),
    ],
    indices = [
        Index(value = ["reportDate", "id"]), Index(value = ["accountId", "reportDate"]),
        Index(value = ["merchantLabel", "reportDate"]), Index(value = ["categoryId", "reportDate"]),
        Index(value = ["status", "reportDate"]), Index("localReferenceDigest"), Index("refundOfId"),
    ],
)
data class LocalTransactionEntity(
    @PrimaryKey val id: UUID,
    val accountId: UUID?,
    val amountPaise: Long,
    val kind: TransactionKind,
    val reportDate: LocalDate,
    val occurredAt: Instant?,
    val observedAt: Instant,
    val dateSource: String,
    val merchantLabel: String?,
    val categoryId: UUID?,
    val beneficiaryLabel: String? = null,
    val localReferenceDigest: String? = null,
    val confidence: String,
    val reasonCode: String,
    val status: TransactionStatus = TransactionStatus.REVIEW,
    val refundOfId: UUID? = null,
    val localVersion: Long = 1,
    val sharedId: UUID? = null,
    val shareVersion: Long? = null,
    val shareState: String = "private",
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        Inr.requireAmount(amountPaise)
        listOf(dateSource, confidence, reasonCode, shareState).forEach(::requireCode)
        merchantLabel?.let { requireLabel(it, 80) }
        beneficiaryLabel?.let { requireLabel(it, 40) }
        localReferenceDigest?.let(::requireDigest)
        require(localVersion > 0 && (shareVersion == null || shareVersion > 0)) { "INVALID_VERSION" }
        require(sharedId != null || shareVersion == null) { "INVALID_SHARE_VERSION" }
        require(occurredAt == null || ReportingDates.reportDate(occurredAt) == reportDate) { "EVENT_DATE_MISMATCH" }
    }
}

enum class ObservationSource { SMS, NOTIFICATION, CSV }
enum class ObservationOutcome { CAPTURED, DUPLICATE, UNSUPPORTED, IGNORED }

@Entity(
    tableName = "capture_observation",
    foreignKeys = [ForeignKey(entity = LocalTransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"])],
    indices = [Index("transactionId"), Index(value = ["source", "observedAt"])],
)
data class CaptureObservationEntity(
    @PrimaryKey val fingerprint: String,
    val transactionId: UUID?,
    val source: ObservationSource,
    val templateId: String?,
    val parserVersion: Int,
    val observedAt: Instant,
    val institutionCode: String?,
    val outcome: ObservationOutcome,
    val reasonCode: String,
) {
    init {
        requireDigest(fingerprint)
        require(parserVersion > 0) { "INVALID_PARSER_VERSION" }
        templateId?.let(::requireCode)
        institutionCode?.let(::requireCode)
        requireCode(reasonCode)
    }
}
