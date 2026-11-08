"""Check the exported first ledger slice; not a Room/SQLCipher device test.

Uses synthetic in-memory SQLite DDL only. Never opens an app database.
"""
from pathlib import Path
import json
import sqlite3

ROOT = Path(__file__).resolve().parents[2]
path = ROOT / "android/core-data/schemas/dev.expensetracker.prototype.data.ledger.LocalLedgerDatabase/1.json"
schema = json.loads(path.read_text())["database"]
assert schema["version"] == 1
expected = {
    "account_alias": "id institutionCode instrumentType maskedSuffix userLabel state createdAt",
    "personal_category": "id seedCode name archivedAt",
    "personal_budget": "categoryId month limitPaise updatedAt",
    "local_transaction": "id accountId amountPaise kind reportDate occurredAt observedAt dateSource merchantLabel categoryId beneficiaryLabel localReferenceDigest confidence reasonCode status refundOfId localVersion sharedId shareVersion shareState createdAt updatedAt",
    "capture_observation": "fingerprint transactionId source templateId parserVersion observedAt institutionCode outcome reasonCode",
}
entities = {entity["tableName"]: entity for entity in schema["entities"]}
assert entities.keys() == expected.keys(), "Unreviewed table change"
for table, names in expected.items():
    assert {field["columnName"] for field in entities[table]["fields"]} == set(names.split()), f"Unreviewed fields: {table}"
for table, amount in (("local_transaction", "amountPaise"), ("personal_budget", "limitPaise")):
    field = next(field for field in entities[table]["fields"] if field["columnName"] == amount)
    assert field["affinity"] == "INTEGER" and field["notNull"], "Amounts must remain integer paise"
assert entities["personal_budget"]["primaryKey"]["columnNames"] == ["categoryId", "month"]
assert entities["capture_observation"]["primaryKey"]["columnNames"] == ["fingerprint"]
assert len(entities["local_transaction"]["foreignKeys"]) == 3
assert len(entities["personal_budget"]["foreignKeys"]) == 1
assert len(entities["capture_observation"]["foreignKeys"]) == 1

with sqlite3.connect(":memory:") as connection:
    connection.execute("PRAGMA foreign_keys = ON")
    for table, entity in entities.items():
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", table))
    assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
print("Exported five-table Room schema checks passed; Android encryption/trigger tests remain separate.")
