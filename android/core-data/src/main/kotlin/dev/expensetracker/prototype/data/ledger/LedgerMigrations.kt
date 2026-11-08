package dev.expensetracker.prototype.data.ledger

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive development migration: all v1 rows, labels, metadata and triggers remain untouched. */
object LedgerMigrations {
    val FROM_1_TO_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS merchant_rule (
                id TEXT NOT NULL PRIMARY KEY, merchantKey TEXT NOT NULL, accountId TEXT,
                categoryId TEXT NOT NULL, updatedAt TEXT NOT NULL,
                FOREIGN KEY(accountId) REFERENCES account_alias(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(categoryId) REFERENCES personal_category(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_merchant_rule_merchantKey_accountId ON merchant_rule(merchantKey, accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_merchant_rule_accountId ON merchant_rule(accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_merchant_rule_categoryId ON merchant_rule(categoryId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS sharing_rule (
                id TEXT NOT NULL PRIMARY KEY, householdId TEXT NOT NULL, merchantKey TEXT, accountId TEXT,
                householdCategoryId TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 0, confirmedAt TEXT NOT NULL,
                FOREIGN KEY(accountId) REFERENCES account_alias(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sharing_rule_householdId_enabled ON sharing_rule(householdId, enabled)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sharing_rule_accountId ON sharing_rule(accountId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS correction_undo (
                operationId TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL,
                createdAt TEXT NOT NULL, expiresAt TEXT NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_correction_undo_expiresAt ON correction_undo(expiresAt)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS coverage_check (
                id TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, fromDate TEXT NOT NULL, toDate TEXT NOT NULL,
                statementDigest TEXT NOT NULL, rowCount INTEGER NOT NULL, matchedCount INTEGER NOT NULL,
                newCount INTEGER NOT NULL, unresolvedCount INTEGER NOT NULL, confirmedAt TEXT,
                FOREIGN KEY(accountId) REFERENCES account_alias(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_coverage_check_accountId ON coverage_check(accountId)")
            LocalRuleConstraints.install(db)
        }
    }
}

/** Nullable scope uniqueness cannot be expressed by Room's ordinary UNIQUE index alone. */
internal object LocalRuleConstraints {
    fun install(db: SupportSQLiteDatabase) {
        for (operation in listOf("INSERT", "UPDATE")) {
            val suffix = operation.lowercase(java.util.Locale.ROOT)
            trigger(db, "merchant_scope_$suffix", operation, "merchant_rule", """
                EXISTS (SELECT 1 FROM merchant_rule WHERE id != NEW.id AND merchantKey = NEW.merchantKey
                    AND accountId IS NEW.accountId)
                """, "MERCHANT_SCOPE_CONFLICT")
            trigger(db, "merchant_category_$suffix", operation, "merchant_rule", """
                NOT EXISTS (SELECT 1 FROM personal_category WHERE id = NEW.categoryId AND archivedAt IS NULL)
                """, "CATEGORY_UNAVAILABLE")
            // Full Unicode NFC/ROOT validation is performed by the typed entity constructors.
            for (table in listOf("merchant_rule", "sharing_rule")) {
                trigger(db, "${table}_key_$suffix", operation, table, """
                    NEW.merchantKey IS NOT NULL AND (length(NEW.merchantKey) NOT BETWEEN 1 AND 80
                        OR NEW.merchantKey != trim(NEW.merchantKey) OR NEW.merchantKey != lower(NEW.merchantKey))
                    """, "INVALID_MERCHANT_KEY")
            }
            trigger(db, "sharing_match_$suffix", operation, "sharing_rule", """
                (NEW.merchantKey IS NULL AND NEW.accountId IS NULL)
                    OR typeof(NEW.enabled) != 'integer' OR NEW.enabled NOT IN (0, 1)
                """, "INVALID_SHARING_RULE")
            trigger(db, "undo_lifetime_$suffix", operation, "correction_undo", """
                NEW.expiresAt <= NEW.createdAt OR julianday(NEW.createdAt) IS NULL
                OR julianday(NEW.expiresAt) IS NULL
                OR NEW.expiresAt > (strftime('%Y-%m-%dT', substr(NEW.createdAt, 1, 10), '+1 day') || substr(NEW.createdAt, 12))
                """, "INVALID_UNDO_LIFETIME")
            trigger(db, "coverage_valid_$suffix", operation, "coverage_check", """
                NEW.fromDate > NEW.toDate OR length(NEW.statementDigest) != 64
                OR NEW.statementDigest GLOB '*[^0-9a-f]*'
                OR typeof(NEW.rowCount) != 'integer' OR NEW.rowCount NOT BETWEEN 0 AND 10000
                OR typeof(NEW.matchedCount) != 'integer' OR NEW.matchedCount NOT BETWEEN 0 AND 10000
                OR typeof(NEW.newCount) != 'integer' OR NEW.newCount NOT BETWEEN 0 AND 10000
                OR typeof(NEW.unresolvedCount) != 'integer' OR NEW.unresolvedCount NOT BETWEEN 0 AND 10000
                OR NEW.matchedCount + NEW.newCount + NEW.unresolvedCount != NEW.rowCount
                OR (NEW.confirmedAt IS NOT NULL AND NEW.unresolvedCount != 0)
                """, "INVALID_COVERAGE")
        }
        trigger(db, "coverage_initial_unconfirmed", "INSERT", "coverage_check",
            "NEW.confirmedAt IS NOT NULL", "COVERAGE_REQUIRES_EXPLICIT_CONFIRMATION")
    }

    private fun trigger(db: SupportSQLiteDatabase, name: String, operation: String, table: String, condition: String, error: String) {
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS $name BEFORE $operation ON $table
            WHEN $condition BEGIN SELECT RAISE(ABORT, '$error'); END""")
    }
}
