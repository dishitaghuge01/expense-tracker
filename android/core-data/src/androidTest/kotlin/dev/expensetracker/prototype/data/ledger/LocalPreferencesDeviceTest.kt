package dev.expensetracker.prototype.data.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.expensetracker.model.CaptureAccess
import dev.expensetracker.model.CaptureConsent
import dev.expensetracker.model.CaptureSource
import dev.expensetracker.model.CaptureState
import dev.expensetracker.model.LedgerKey
import dev.expensetracker.model.LedgerKind
import dev.expensetracker.model.SourcePermission
import dev.expensetracker.prototype.data.DatabaseKeyVault
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic encrypted Room state only. Host compilation intended; device execution deferred. */
@RunWith(AndroidJUnit4::class)
class LocalPreferencesDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.parse("2026-11-08T12:34:56.123456789Z")
    private fun id() = UUID.randomUUID()
    private fun health(source: CaptureSource, permission: SourcePermission = SourcePermission.GRANTED) =
        SourceHealthSnapshot(source, permission, now.minusNanos(1), now)
    private fun progress() = HistoryScanProgress(now.minus(Duration.ofDays(90)), now, 123, 250, false)
    private fun withDatabase(test: suspend (String, LocalLedgerDatabase) -> Unit) = runBlocking {
        val subject = "synthetic-preferences-${id()}"
        val file = DatabaseKeyVault(context).databaseFile(subject)
        val handle = LocalLedgerDatabase.open(context, subject)
        try { test(subject, handle.database) } finally {
            handle.close()
            listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
            listOf(".key", ".key.bak", ".key.new").forEach { File(file.path.removeSuffix(".db") + it).delete() }
        }
    }

    @Test fun settersDoNotCreateOrActivateMissingSingleton() = withDatabase { _, db ->
        val dao = db.preferences()
        assertNull(dao.settings())
        assertEquals(0, dao.setCaptureConsent(CaptureConsent(true, true, true)))
        assertEquals(0, dao.setCapturePaused(true))
        assertEquals(0, dao.setPresetPacks(listOf(PresetPack.STUDENT)))
        assertEquals(0, dao.setLocalLock(false, false))
        assertEquals(0, dao.recordSourceHealth(health(CaptureSource.LIVE_SMS)))
        assertEquals(0, dao.setHistoryScan(progress()))
        assertEquals(0, dao.setHistoryScan(null))
        assertEquals(0, dao.setLastCheckAt(now))
        assertNull(dao.settings())
    }

    @Test fun initializeDefaultsFollowActualLockAndRepeatPreservesEveryChoice() = withDatabase { subject, db ->
        val dao = db.preferences()
        assertEquals(LocalSettingsEntity(localLockEnabled = true), dao.initializeSettings(true))
        assertEquals(1, dao.setCaptureConsent(CaptureConsent(history = true)))
        assertEquals(1, dao.setCapturePaused(true))
        assertEquals(1, dao.setPresetPacks(listOf(PresetPack.STUDENT, PresetPack.SALARIED)))
        assertEquals(1, dao.setLocalLock(false, true))
        assertEquals(1, dao.recordSourceHealth(health(CaptureSource.SMS_HISTORY)))
        assertEquals(1, dao.setHistoryScan(progress()))
        assertEquals(1, dao.setLastCheckAt(now))
        val expected = LocalSettingsEntity(historyOptIn = true, capturePaused = true,
            selectedPacks = listOf(PresetPack.STUDENT, PresetPack.SALARIED), localLockEnabled = false,
            sourceHealth = listOf(health(CaptureSource.SMS_HISTORY)), historyScan = progress(), lastCheckAt = now)
        assertEquals(expected, dao.settings())
        assertEquals(expected, dao.initializeSettings(false))
        assertEquals(expected, dao.initializeSettings(true))
        LocalLedgerDatabase.open(context, subject).use { reopened ->
            assertEquals(expected, reopened.database.preferences().settings())
            assertEquals(expected, reopened.database.preferences().initializeSettings(true))
        }
    }

    @Test fun concurrentInitializeIsInsertIfAbsentAndLockCannotBeEnabledWithoutDeviceLock() = withDatabase { _, db ->
        val dao = db.preferences()
        val results = coroutineScope { (1..12).map { async(Dispatchers.IO) { dao.initializeSettings(false) } }.awaitAll() }
        assertTrue(results.all { it == LocalSettingsEntity(localLockEnabled = false) })
        val error = assertThrows(IllegalArgumentException::class.java) { runBlocking { dao.setLocalLock(true, false) } }
        assertTrue((error.message ?: "").length <= 200)
        assertNull(error.cause)
        assertFalse(dao.settings()!!.localLockEnabled)
        assertEquals(1, dao.setLocalLock(true, true))
        assertTrue(dao.initializeSettings(false).localLockEnabled)
        assertEquals(1, dao.setLocalLock(false, false))
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_settings").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    @Test fun independentConsentAndHealthUpdatesPreserveUnrelatedStateAndGrantNoAuthority() = withDatabase { _, db ->
        val dao = db.preferences()
        dao.initializeSettings(true)
        dao.setCapturePaused(true)
        dao.setPresetPacks(listOf(PresetPack.CAREGIVING_RETIREMENT))
        dao.setHistoryScan(progress())
        dao.setLastCheckAt(now)
        val before = dao.settings()!!
        dao.recordSourceHealth(health(CaptureSource.LIVE_SMS))
        dao.recordSourceHealth(health(CaptureSource.NOTIFICATIONS, SourcePermission.DENIED))
        val updated = health(CaptureSource.LIVE_SMS, SourcePermission.BLOCKED).copy(checkedAt = now.plusNanos(1))
        assertEquals(1, dao.recordSourceHealth(updated))
        val state = dao.settings()!!
        assertEquals(before, state.copy(sourceHealth = emptyList()))
        assertEquals(setOf(updated, health(CaptureSource.NOTIFICATIONS, SourcePermission.DENIED)), state.sourceHealth.toSet())
        assertEquals(CaptureState.NOT_ACTIVATED, CaptureAccess().state(CaptureSource.LIVE_SMS, SourcePermission.GRANTED))
        for (consent in listOf(CaptureConsent(liveSms = true), CaptureConsent(history = true), CaptureConsent(notifications = true), CaptureConsent())) {
            val old = dao.settings()!!
            assertEquals(1, dao.setCaptureConsent(consent))
            assertEquals(old.copy(liveSmsOptIn = consent.liveSms, historyOptIn = consent.history, notificationsOptIn = consent.notifications), dao.settings())
        }
        val old = dao.settings()!!
        assertEquals(1, dao.setCapturePaused(false))
        assertEquals(old.copy(capturePaused = false), dao.settings())
        val unpaused = dao.settings()!!
        assertEquals(1, dao.setPresetPacks(listOf(PresetPack.SELF_EMPLOYED)))
        assertEquals(unpaused.copy(selectedPacks = listOf(PresetPack.SELF_EMPLOYED)), dao.settings())
        val packed = dao.settings()!!
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dao.setPresetPacks(listOf(PresetPack.STUDENT, PresetPack.STUDENT)) }
        }
        assertEquals(packed, dao.settings())
        assertEquals(1, dao.setLastCheckAt(now.plusNanos(1)))
        assertEquals(packed.copy(lastCheckAt = now.plusNanos(1)), dao.settings())
        val checked = dao.settings()!!
        assertEquals(1, dao.setLocalLock(false, true))
        assertEquals(checked.copy(localLockEnabled = false), dao.settings())
    }

    @Test fun concurrentHealthReplacementAndScanCheckpointsDoNotEnablePermissionsOrConsent() = withDatabase { subject, db ->
        val dao = db.preferences()
        dao.initializeSettings(false)
        coroutineScope { CaptureSource.entries.map { source -> async(Dispatchers.IO) { dao.recordSourceHealth(health(source)) } }.awaitAll() }
        assertEquals(CaptureSource.entries.toSet(), dao.settings()!!.sourceHealth.map { it.source }.toSet())
        assertEquals(3, dao.settings()!!.sourceHealth.size)
        val before = dao.settings()!!
        val checkpoint = progress().copy(lastProcessedSmsId = 456, processedCount = 500, complete = true)
        assertEquals(1, dao.setHistoryScan(checkpoint))
        assertEquals(before.copy(historyScan = checkpoint), dao.settings())
        LocalLedgerDatabase.open(context, subject).use { reopened -> assertEquals(before.copy(historyScan = checkpoint), reopened.database.preferences().settings()) }
        assertEquals(1, dao.setHistoryScan(null))
        assertEquals(before, dao.settings())
    }

    @Test fun budgetMarkersAreOnceOnlyEvenConcurrentlyAndIndependentByFullCompositeIdentity() = withDatabase { subject, db ->
        val dao = db.preferences()
        val owner = id()
        val scope = LedgerKey(LedgerKind.PERSONAL, owner)
        val alert = BudgetAlertEntity(scope, id(), YearMonth.of(2026, 11), 80, now)
        val attempts = coroutineScope { (1..16).map { async(Dispatchers.IO) { dao.tryRecordBudgetAlert(alert) } }.awaitAll() }
        assertEquals(1, attempts.count { it })
        assertFalse(dao.tryRecordBudgetAlert(alert.copy(emittedAt = now.plusSeconds(1))))
        db.openHelper.readableDatabase.query("SELECT emittedAt FROM budget_alerts").use { it.moveToFirst(); assertEquals(LedgerConverters().instantToText(now), it.getString(0)) }
        val independent = listOf(alert.copy(threshold = 100), alert.copy(scope = LedgerKey(LedgerKind.HOUSEHOLD, owner)),
            alert.copy(scope = LedgerKey(LedgerKind.PERSONAL, id())), alert.copy(scope = LedgerKey(LedgerKind.HOUSEHOLD, id())),
            alert.copy(categoryId = id()), alert.copy(month = alert.month.plusMonths(1)))
        for (other in independent) assertTrue(dao.tryRecordBudgetAlert(other))
        assertEquals(listOf(80, 100), dao.emittedThresholds(scope, alert.categoryId, alert.month))
        for (other in independent.drop(1)) assertEquals(listOf(80), dao.emittedThresholds(other.scope, other.categoryId, other.month))
        assertEquals(emptyList<Int>(), dao.emittedThresholds(scope, alert.categoryId, alert.month.minusMonths(1)))
        assertNull(dao.settings()) // Emission bookkeeping is not settings initialization or activation.
        LocalLedgerDatabase.open(context, subject).use { reopened ->
            assertEquals(listOf(80, 100), reopened.database.preferences().emittedThresholds(scope, alert.categoryId, alert.month))
            assertFalse(reopened.database.preferences().tryRecordBudgetAlert(alert.copy(emittedAt = now.plusSeconds(2))))
        }
    }

    @Test fun sqlCannotBypassSingletonBooleanOrThresholdConstraints() = withDatabase { _, db ->
        db.preferences().initializeSettings(false)
        val sql = db.openHelper.writableDatabase
        for (assignment in listOf("id=2", "liveSmsOptIn=2", "historyOptIn=-1", "notificationsOptIn=1.5", "capturePaused=2", "localLockEnabled=-1")) {
            assertThrows(RuntimeException::class.java) { sql.execSQL("UPDATE local_settings SET $assignment") }
        }
        assertThrows(RuntimeException::class.java) { sql.execSQL("INSERT INTO local_settings (id,liveSmsOptIn,historyOptIn,notificationsOptIn,capturePaused,selectedPacks,sourceHealth,localLockEnabled,historyScan,lastCheckAt) SELECT 2,liveSmsOptIn,historyOptIn,notificationsOptIn,capturePaused,selectedPacks,sourceHealth,localLockEnabled,historyScan,lastCheckAt FROM local_settings") }
        val alert = BudgetAlertEntity(LedgerKey(LedgerKind.HOUSEHOLD, id()), id(), YearMonth.of(2026, 11), 80, now)
        assertTrue(db.preferences().tryRecordBudgetAlert(alert))
        assertThrows(RuntimeException::class.java) { sql.execSQL("UPDATE budget_alerts SET threshold=81") }
        assertThrows(RuntimeException::class.java) { sql.execSQL("UPDATE budget_alerts SET threshold=80.5") }
        assertEquals(listOf(80), db.preferences().emittedThresholds(alert.scope, alert.categoryId, alert.month))
    }
}
