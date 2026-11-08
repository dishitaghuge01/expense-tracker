package dev.expensetracker.prototype.data.ledger

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.expensetracker.model.CaptureSource
import dev.expensetracker.model.LedgerKey
import dev.expensetracker.model.ReportingDates
import dev.expensetracker.model.SourcePermission
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable(with = PresetPackSerializer::class)
enum class PresetPack { SCHOOL_AGE_HOUSEHOLD, STUDENT, SALARIED, SELF_EMPLOYED, CAREGIVING_RETIREMENT }

/** Cached health metadata only: never a lease, activation, or authority to read alerts. */
@Serializable
data class SourceHealthSnapshot(
    @Serializable(with = SettingsSourceSerializer::class) val source: CaptureSource,
    @Serializable(with = SettingsPermissionSerializer::class) val permission: SourcePermission,
    @Serializable(with = SettingsInstantSerializer::class) val lastCapturedAt: Instant?,
    @Serializable(with = SettingsInstantSerializer::class) val checkedAt: Instant,
) {
    init {
        lastCapturedAt?.let(::requireSettingsInstant)
        requireSettingsInstant(checkedAt)
    }
}

@Serializable
data class HistoryScanProgress(
    @Serializable(with = SettingsInstantSerializer::class) val fromReceivedAt: Instant,
    @Serializable(with = SettingsInstantSerializer::class) val toReceivedAt: Instant,
    @Serializable(with = SettingsLongSerializer::class) val lastProcessedSmsId: Long? = null,
    @Serializable(with = SettingsLongSerializer::class) val processedCount: Long = 0,
    @Serializable(with = SettingsBooleanSerializer::class) val complete: Boolean = false,
) {
    init {
        requireSettingsInstant(fromReceivedAt)
        requireSettingsInstant(toReceivedAt)
        require(!fromReceivedAt.isAfter(toReceivedAt) &&
            Duration.between(fromReceivedAt, toReceivedAt) <= Duration.ofDays(90)) { "INVALID_HISTORY_RANGE" }
        require(lastProcessedSmsId == null || lastProcessedSmsId > 0) { "INVALID_HISTORY_CURSOR" }
        require(processedCount >= 0) { "INVALID_HISTORY_COUNT" }
    }
}

internal fun requireSettingsInstant(value: Instant) {
    ReportingDates.parseUtcInstant(value.toString())
}

internal fun requirePacks(packs: List<PresetPack>) {
    require(packs.distinct().size == packs.size) { "DUPLICATE_PRESET_PACK" }
}

internal fun requireSourceHealth(health: List<SourceHealthSnapshot>) {
    require(health.size <= 3 && health.map { it.source }.distinct().size == health.size) { "INVALID_SOURCE_HEALTH" }
}

@Entity(tableName = "local_settings")
data class LocalSettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "0") val liveSmsOptIn: Boolean = false,
    @ColumnInfo(defaultValue = "0") val historyOptIn: Boolean = false,
    @ColumnInfo(defaultValue = "0") val notificationsOptIn: Boolean = false,
    @ColumnInfo(defaultValue = "0") val capturePaused: Boolean = false,
    val selectedPacks: List<PresetPack> = emptyList(),
    val sourceHealth: List<SourceHealthSnapshot> = emptyList(),
    val localLockEnabled: Boolean,
    val historyScan: HistoryScanProgress? = null,
    val lastCheckAt: Instant? = null,
) {
    init {
        require(id == 1) { "INVALID_SETTINGS_ID" }
        requirePacks(selectedPacks)
        requireSourceHealth(sourceHealth)
        lastCheckAt?.let(::requireSettingsInstant)
    }
}

@Entity(tableName = "budget_alerts", primaryKeys = ["scope", "categoryId", "month", "threshold"])
data class BudgetAlertEntity(
    val scope: LedgerKey,
    val categoryId: UUID,
    val month: YearMonth,
    val threshold: Int,
    val emittedAt: Instant,
) {
    init {
        ReportingDates.parseMonth(month.toString())
        require(threshold == 80 || threshold == 100) { "INVALID_BUDGET_THRESHOLD" }
        requireSettingsInstant(emittedAt)
    }
}
