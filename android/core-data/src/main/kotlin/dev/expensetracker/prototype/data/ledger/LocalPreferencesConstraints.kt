package dev.expensetracker.prototype.data.ledger

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

internal object LocalPreferencesConstraints {
    fun install(db: SupportSQLiteDatabase) {
        for (operation in listOf("INSERT", "UPDATE")) {
            val suffix = operation.lowercase(Locale.ROOT)
            val invalidBoolean = listOf(
                "liveSmsOptIn", "historyOptIn", "notificationsOptIn", "capturePaused", "localLockEnabled",
            ).joinToString(" OR ") { "typeof(NEW.$it) != 'integer' OR NEW.$it NOT IN (0, 1)" }
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS local_settings_valid_$suffix
                BEFORE $operation ON local_settings
                WHEN typeof(NEW.id) != 'integer' OR NEW.id != 1 OR $invalidBoolean
                BEGIN SELECT RAISE(ABORT, 'INVALID_LOCAL_SETTINGS'); END""")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS budget_alert_threshold_$suffix
                BEFORE $operation ON budget_alerts
                WHEN typeof(NEW.threshold) != 'integer' OR NEW.threshold NOT IN (80, 100)
                BEGIN SELECT RAISE(ABORT, 'INVALID_BUDGET_THRESHOLD'); END""")
        }
    }
}
