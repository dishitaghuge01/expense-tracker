package dev.expensetracker.prototype.data.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.expensetracker.model.CaptureConsent
import dev.expensetracker.model.LedgerKey
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/** Inactive local preferences and emission markers, never capture authority or notification delivery. */
@Dao
abstract class LocalPreferencesDao {
    @Query("SELECT * FROM local_settings WHERE id = 1")
    abstract suspend fun settings(): LocalSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertInitialSettings(settings: LocalSettingsEntity): Long

    /** Repeat/concurrent calls preserve existing user choices; no opt-in is inferred from device state. */
    @Transaction
    open suspend fun initializeSettings(deviceHasScreenLock: Boolean): LocalSettingsEntity {
        insertInitialSettings(LocalSettingsEntity(localLockEnabled = deviceHasScreenLock))
        return requireNotNull(settings()) { "SETTINGS_UNAVAILABLE" }
    }

    @Query("""UPDATE local_settings SET liveSmsOptIn = :liveSms,
        historyOptIn = :history, notificationsOptIn = :notifications WHERE id = 1""")
    protected abstract suspend fun setCaptureConsentRow(liveSms: Boolean, history: Boolean, notifications: Boolean): Int

    suspend fun setCaptureConsent(consent: CaptureConsent): Int =
        setCaptureConsentRow(consent.liveSms, consent.history, consent.notifications)

    @Query("UPDATE local_settings SET capturePaused = :paused WHERE id = 1")
    abstract suspend fun setCapturePaused(paused: Boolean): Int

    @Query("UPDATE local_settings SET selectedPacks = :packs WHERE id = 1")
    protected abstract suspend fun setPresetPacksRow(packs: List<PresetPack>): Int

    suspend fun setPresetPacks(packs: List<PresetPack>): Int {
        requirePacks(packs)
        return setPresetPacksRow(packs)
    }

    @Query("UPDATE local_settings SET localLockEnabled = :enabled WHERE id = 1")
    protected abstract suspend fun setLocalLockRow(enabled: Boolean): Int

    suspend fun setLocalLock(enabled: Boolean, deviceHasScreenLock: Boolean): Int {
        require(!enabled || deviceHasScreenLock) { "DEVICE_SCREEN_LOCK_REQUIRED" }
        return setLocalLockRow(enabled)
    }

    @Query("UPDATE local_settings SET sourceHealth = :health WHERE id = 1")
    protected abstract suspend fun setSourceHealthRow(health: List<SourceHealthSnapshot>): Int

    /** Updates cached metadata only; the independent consent/pause/lock columns are never touched. */
    @Transaction
    open suspend fun recordSourceHealth(snapshot: SourceHealthSnapshot): Int {
        val current = settings() ?: return 0
        val replaced = current.sourceHealth.any { it.source == snapshot.source }
        val health = current.sourceHealth.map { if (it.source == snapshot.source) snapshot else it }
        return setSourceHealthRow(if (replaced) health else health + snapshot)
    }

    @Query("UPDATE local_settings SET historyScan = :progress WHERE id = 1")
    abstract suspend fun setHistoryScan(progress: HistoryScanProgress?): Int

    @Query("UPDATE local_settings SET lastCheckAt = :at WHERE id = 1")
    abstract suspend fun setLastCheckAt(at: Instant): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertBudgetAlert(alert: BudgetAlertEntity): Long

    /** A duplicate never replaces its timestamp; posting/scheduling is outside this DAO. */
    suspend fun tryRecordBudgetAlert(alert: BudgetAlertEntity): Boolean = insertBudgetAlert(alert) != -1L

    @Query("""SELECT threshold FROM budget_alerts WHERE scope = :scope
        AND categoryId = :categoryId AND month = :month ORDER BY threshold ASC""")
    abstract suspend fun emittedThresholds(scope: LedgerKey, categoryId: UUID, month: YearMonth): List<Int>
}
