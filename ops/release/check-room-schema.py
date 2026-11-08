"""Check exported development ledger schemas; not a Room/SQLCipher device test.

Uses synthetic in-memory SQLite DDL only. Never opens an app database.
"""
from pathlib import Path
import hashlib
import json
import sqlite3

ROOT = Path(__file__).resolve().parents[2]
path = ROOT / "android/core-data/schemas/dev.expensetracker.prototype.data.ledger.LocalLedgerDatabase/1.json"
assert hashlib.sha256(path.read_bytes()).hexdigest() == "2eaa0da4656cf19c803f6f2dbc53c41a29cf41020615f5f0791a59cd2e6861b3", "Preserve the committed v1 export"
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
# Preserve historical exports. Never synthesize them from current production DDL.
v2_path = path.with_name("2.json")
assert v2_path.is_file(), "Generate and review Room schema 2.json before integration validation"
assert hashlib.sha256(v2_path.read_bytes()).hexdigest() == "067aabdd73eff08f8e945b50e8cd962922a987b3be3aaa6d42aecbdfad9c502f", "Preserve the committed v2 export"
v2 = json.loads(v2_path.read_text())["database"]
assert v2["version"] == 2
v2_expected = expected | {
    "merchant_rule": "id merchantKey accountId categoryId updatedAt",
    "sharing_rule": "id householdId merchantKey accountId householdCategoryId enabled confirmedAt",
    "correction_undo": "operationId payload createdAt expiresAt",
    "coverage_check": "id accountId fromDate toDate statementDigest rowCount matchedCount newCount unresolvedCount confirmedAt",
}
v2_entities = {entity["tableName"]: entity for entity in v2["entities"]}
assert v2_entities.keys() == v2_expected.keys(), "Expected exactly nine local tables; cache/outbox remain pending"
for table, names in v2_expected.items():
    assert {field["columnName"] for field in v2_entities[table]["fields"]} == set(names.split()), f"Unreviewed v2 fields: {table}"
for table in expected:
    assert v2_entities[table] == entities[table], f"Additive migration must preserve v1 table: {table}"
for table, columns in {
    "local_transaction": "amountPaise localVersion shareVersion",
    "personal_budget": "limitPaise",
    "capture_observation": "parserVersion",
    "sharing_rule": "enabled",
    "coverage_check": "rowCount matchedCount newCount unresolvedCount",
}.items():
    fields = {field["columnName"]: field for field in v2_entities[table]["fields"]}
    for column in columns.split():
        assert fields[column]["affinity"] == "INTEGER", f"Integer storage required: {table}.{column}"
        assert fields[column].get("notNull", False) == (column != "shareVersion")
for table, primary_key in {
    "merchant_rule": "id", "sharing_rule": "id", "correction_undo": "operationId", "coverage_check": "id",
}.items():
    assert v2_entities[table]["primaryKey"]["columnNames"] == [primary_key]
    assert not v2_entities[table]["primaryKey"].get("autoGenerate", False)
for table, expected_fks in {
    "merchant_rule": {("accountId", "account_alias", "id"), ("categoryId", "personal_category", "id")},
    "sharing_rule": {("accountId", "account_alias", "id")},
    "correction_undo": set(),
    "coverage_check": {("accountId", "account_alias", "id")},
}.items():
    fks = v2_entities[table].get("foreignKeys", [])
    assert {(fk["columns"][0], fk["table"], fk["referencedColumns"][0]) for fk in fks} == expected_fks
    assert all(len(fk["columns"]) == len(fk["referencedColumns"]) == 1 for fk in fks)
    assert all(fk["onDelete"] == fk["onUpdate"] == "NO ACTION" for fk in fks)
    for column, _, _ in expected_fks:
        assert any(index["columnNames"][0] == column for index in v2_entities[table].get("indices", [])), f"Unindexed join: {table}.{column}"
for table, nullable in {
    "merchant_rule": {"accountId"}, "sharing_rule": {"merchantKey", "accountId"},
    "correction_undo": set(), "coverage_check": {"confirmedAt"},
}.items():
    for field in v2_entities[table]["fields"]:
        column = field["columnName"]
        assert field.get("notNull", False) == (column not in nullable), f"Unexpected nullability: {table}.{column}"
        if column not in {"enabled", "rowCount", "matchedCount", "newCount", "unresolvedCount"}:
            assert field["affinity"] == "TEXT", f"Expected normalized TEXT: {table}.{column}"
assert next(field for field in v2_entities["sharing_rule"]["fields"] if field["columnName"] == "enabled")["defaultValue"] == "0"
assert any(index["columnNames"] == ["householdId", "enabled"] for index in v2_entities["sharing_rule"].get("indices", []))
assert any(index["columnNames"][0] == "expiresAt" for index in v2_entities["correction_undo"].get("indices", []))
assert any(index["columnNames"] == ["merchantKey", "accountId"] and index["unique"]
           for index in v2_entities["merchant_rule"].get("indices", []))
with sqlite3.connect(":memory:") as connection:
    connection.execute("PRAGMA foreign_keys = ON")
    for table, entity in v2_entities.items():
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", table))
    assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
# The verifier generates 3.json via KSP after source integration; missing means failure.
v3_path = path.with_name("3.json")
assert v3_path.is_file(), "Generate and review Room schema 3.json before integration validation"
v3 = json.loads(v3_path.read_text())["database"]
assert v3["version"] == 3
v3_expected = v2_expected | {
    "local_settings": "id liveSmsOptIn historyOptIn notificationsOptIn capturePaused selectedPacks sourceHealth localLockEnabled historyScan lastCheckAt",
    "budget_alerts": "scope categoryId month threshold emittedAt",
}
v3_entities = {entity["tableName"]: entity for entity in v3["entities"]}
assert v3_entities.keys() == v3_expected.keys(), "Expected exactly eleven local tables; full product schema remains pending"
for table, names in v3_expected.items():
    assert {field["columnName"] for field in v3_entities[table]["fields"]} == set(names.split()), f"Unreviewed v3 fields: {table}"
for table in v2_expected:
    assert v3_entities[table] == v2_entities[table], f"Additive migration must preserve v2 table: {table}"
settings = v3_entities["local_settings"]
alerts = v3_entities["budget_alerts"]
assert settings["primaryKey"]["columnNames"] == ["id"]
assert alerts["primaryKey"]["columnNames"] == ["scope", "categoryId", "month", "threshold"]
for entity in (settings, alerts):
    assert not entity["primaryKey"].get("autoGenerate", False)
    assert entity.get("foreignKeys", []) == [], "No FK to personal-only categories or invented cloud caches"
for table, integer_columns, nullable in (
    ("local_settings", {"id", "liveSmsOptIn", "historyOptIn", "notificationsOptIn", "capturePaused", "localLockEnabled"}, {"historyScan", "lastCheckAt"}),
    ("budget_alerts", {"threshold"}, set()),
):
    for field in v3_entities[table]["fields"]:
        column = field["columnName"]
        assert field["affinity"] == ("INTEGER" if column in integer_columns else "TEXT"), f"Unexpected affinity: {table}.{column}"
        assert field.get("notNull", False) == (column not in nullable), f"Unexpected nullability: {table}.{column}"
        if column in {"liveSmsOptIn", "historyOptIn", "notificationsOptIn", "capturePaused"}:
            assert field["defaultValue"] == "0", f"Capture flags must default off: {column}"
        if column == "localLockEnabled":
            assert field.get("defaultValue") in (None, "undefined"), "Lock choice must come from actual device state"
with sqlite3.connect(":memory:") as connection:
    connection.execute("PRAGMA foreign_keys = ON")
    for table, entity in v3_entities.items():
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", table))
    assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
    for table in ("local_settings", "budget_alerts"):
        info = connection.execute(f"PRAGMA table_info({table})").fetchall()
        assert all(row[3] == 1 for row in info if row[5] > 0), f"Non-null primary key required: {table}"
print("Exported v1/v2/v3 Room schema checks passed; native Android encryption, migrations and runtime callbacks/triggers remain separate.")
