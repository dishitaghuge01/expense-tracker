package dev.expensetracker.prototype.data.ledger

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.expensetracker.model.Inr
import dev.expensetracker.prototype.data.EncryptedDatabaseHandle
import dev.expensetracker.prototype.data.EncryptedRoomFactory

/** Local ledger and inactive rule storage; cache, sync and capture remain pending. */
@Database(
    entities = [AccountAliasEntity::class, PersonalCategoryEntity::class, PersonalBudgetEntity::class,
        LocalTransactionEntity::class, CaptureObservationEntity::class,
        MerchantRuleEntity::class, SharingRuleEntity::class, CorrectionUndoEntity::class, CoverageCheckEntity::class,
        LocalSettingsEntity::class, BudgetAlertEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(LedgerConverters::class)
abstract class LocalLedgerDatabase : RoomDatabase() {
    abstract fun ledger(): LocalLedgerDao
    abstract fun rules(): LocalRulesDao
    abstract fun preferences(): LocalPreferencesDao

    companion object {
        @WorkerThread
        fun open(context: Context, authenticatedSubject: String): EncryptedDatabaseHandle<LocalLedgerDatabase> =
            EncryptedRoomFactory(context).open(
                authenticatedSubject, LocalLedgerDatabase::class.java,
                LedgerMigrations.FROM_1_TO_2, LedgerMigrations.FROM_2_TO_3,
                callbacks = listOf(LedgerConstraints),
            )
    }
}

/** SQLite triggers preserve the documented partial active-name constraint without extra entity columns. */
internal object LedgerConstraints : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = install(db)
    override fun onOpen(db: SupportSQLiteDatabase) = install(db)

    private fun install(db: SupportSQLiteDatabase) {
        LocalRuleConstraints.install(db)
        LocalPreferencesConstraints.install(db)
        for (operation in listOf("INSERT", "UPDATE")) {
            val suffix = operation.lowercase(java.util.Locale.ROOT)
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS category_active_name_$suffix
                BEFORE $operation ON personal_category
                WHEN NEW.archivedAt IS NULL AND EXISTS (
                    SELECT 1 FROM personal_category WHERE archivedAt IS NULL
                    AND lower(name) = lower(NEW.name) AND id != NEW.id)
                BEGIN SELECT RAISE(ABORT, 'CATEGORY_NAME_CONFLICT'); END""")
            for ((table, column) in listOf("local_transaction" to "amountPaise", "personal_budget" to "limitPaise")) {
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS ${table}_amount_$suffix
                    BEFORE $operation ON $table
                    WHEN typeof(NEW.$column) != 'integer' OR NEW.$column < 1 OR NEW.$column > ${Inr.MAX_AMOUNT_PAISE}
                    BEGIN SELECT RAISE(ABORT, 'AMOUNT_OUT_OF_RANGE'); END""")
            }
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS captured_amount_date_immutable
            BEFORE UPDATE ON local_transaction
            WHEN NEW.amountPaise != OLD.amountPaise OR NEW.reportDate != OLD.reportDate
            BEGIN SELECT RAISE(ABORT, 'IMMUTABLE_PAYMENT_FIELDS'); END""")
    }
}
