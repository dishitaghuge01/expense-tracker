package dev.expensetracker.prototype.data.ledger

import androidx.sqlite.db.SupportSQLiteDatabase

/** Frozen v1 DDL from the committed 1.json (SHA-256
 * 2eaa0da4656cf19c803f6f2dbc53c41a29cf41020615f5f0791a59cd2e6861b3).
 * Deliberately independent of current entities, migrations and Room callbacks.
 */
internal object LedgerV1Fixture {
    fun create(db: SupportSQLiteDatabase) {
        val tables = listOf(
            "CREATE TABLE account_alias (id TEXT NOT NULL PRIMARY KEY, institutionCode TEXT NOT NULL, instrumentType TEXT NOT NULL, maskedSuffix TEXT, userLabel TEXT NOT NULL, state TEXT NOT NULL, createdAt TEXT NOT NULL)",
            "CREATE TABLE personal_category (id TEXT NOT NULL PRIMARY KEY, seedCode TEXT, name TEXT NOT NULL, archivedAt TEXT)",
            "CREATE TABLE personal_budget (categoryId TEXT NOT NULL, month TEXT NOT NULL, limitPaise INTEGER NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(categoryId,month), FOREIGN KEY(categoryId) REFERENCES personal_category(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
            "CREATE TABLE local_transaction (id TEXT NOT NULL PRIMARY KEY, accountId TEXT, amountPaise INTEGER NOT NULL, kind TEXT NOT NULL, reportDate TEXT NOT NULL, occurredAt TEXT, observedAt TEXT NOT NULL, dateSource TEXT NOT NULL, merchantLabel TEXT, categoryId TEXT, beneficiaryLabel TEXT, localReferenceDigest TEXT, confidence TEXT NOT NULL, reasonCode TEXT NOT NULL, status TEXT NOT NULL, refundOfId TEXT, localVersion INTEGER NOT NULL, sharedId TEXT, shareVersion INTEGER, shareState TEXT NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, FOREIGN KEY(accountId) REFERENCES account_alias(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(categoryId) REFERENCES personal_category(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(refundOfId) REFERENCES local_transaction(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
            "CREATE TABLE capture_observation (fingerprint TEXT NOT NULL PRIMARY KEY, transactionId TEXT, source TEXT NOT NULL, templateId TEXT, parserVersion INTEGER NOT NULL, observedAt TEXT NOT NULL, institutionCode TEXT, outcome TEXT NOT NULL, reasonCode TEXT NOT NULL, FOREIGN KEY(transactionId) REFERENCES local_transaction(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
        )
        tables.forEach(db::execSQL)
        val indices = mapOf(
            "personal_category" to listOf("name", "seedCode"),
            "personal_budget" to listOf("month"),
            "local_transaction" to listOf("reportDate,id", "accountId,reportDate", "merchantLabel,reportDate", "categoryId,reportDate", "status,reportDate", "localReferenceDigest", "refundOfId"),
            "capture_observation" to listOf("transactionId", "source,observedAt"),
        )
        for ((table, columns) in indices) for (column in columns) {
            db.execSQL("CREATE INDEX index_${table}_${column.replace(',', '_')} ON $table ($column)")
        }
        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT INTO room_master_table VALUES(42, '381874798e240c3088f5f6a019e16a4a')")
        // Freeze the v1 runtime trigger contract too, rather than calling current production code.
        for (operation in listOf("INSERT", "UPDATE")) {
            val suffix = operation.lowercase(java.util.Locale.ROOT)
            db.execSQL("""CREATE TRIGGER category_active_name_$suffix BEFORE $operation ON personal_category
                WHEN NEW.archivedAt IS NULL AND EXISTS (SELECT 1 FROM personal_category WHERE archivedAt IS NULL
                AND lower(name) = lower(NEW.name) AND id != NEW.id)
                BEGIN SELECT RAISE(ABORT, 'CATEGORY_NAME_CONFLICT'); END""")
            for ((table, column) in listOf("local_transaction" to "amountPaise", "personal_budget" to "limitPaise")) {
                db.execSQL("""CREATE TRIGGER ${table}_amount_$suffix BEFORE $operation ON $table
                    WHEN typeof(NEW.$column) != 'integer' OR NEW.$column < 1 OR NEW.$column > 100000000000
                    BEGIN SELECT RAISE(ABORT, 'AMOUNT_OUT_OF_RANGE'); END""")
            }
        }
        db.execSQL("""CREATE TRIGGER captured_amount_date_immutable BEFORE UPDATE ON local_transaction
            WHEN NEW.amountPaise != OLD.amountPaise OR NEW.reportDate != OLD.reportDate
            BEGIN SELECT RAISE(ABORT, 'IMMUTABLE_PAYMENT_FIELDS'); END""")
    }
}
