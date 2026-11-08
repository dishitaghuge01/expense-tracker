package dev.expensetracker.prototype.data.ledger

import dev.expensetracker.model.CaptureAccess
import dev.expensetracker.model.CaptureSource
import dev.expensetracker.model.CaptureState
import dev.expensetracker.model.LedgerKey
import dev.expensetracker.model.LedgerKind
import dev.expensetracker.model.SourcePermission
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class LocalPreferencesValuesTest {
    private val now = Instant.parse("2026-11-08T12:34:56.123456789Z")
    private val converters = LedgerConverters()
    private fun health(source: CaptureSource = CaptureSource.LIVE_SMS) =
        SourceHealthSnapshot(source, SourcePermission.GRANTED, now.minusNanos(1), now)
    private fun scan() = HistoryScanProgress(now.minus(Duration.ofDays(90)), now)
    private fun invalid(block: () -> Unit) { assertThrows(IllegalArgumentException::class.java) { block() } }
    private fun bounded(block: () -> Unit) {
        val error = assertThrows(IllegalArgumentException::class.java) { block() }
        assertTrue((error.message ?: "").length <= 200)
        assertFalse((error.message ?: "").contains("SYNTHETIC_PRIVATE_MARKER"))
        assertNull(error.cause)
    }

    @Test fun `settings default private and lock choice is explicit`() {
        val settings = LocalSettingsEntity(localLockEnabled = false)
        assertEquals(1, settings.id)
        assertFalse(settings.liveSmsOptIn)
        assertFalse(settings.historyOptIn)
        assertFalse(settings.notificationsOptIn)
        assertFalse(settings.capturePaused)
        assertFalse(settings.localLockEnabled)
        assertTrue(settings.selectedPacks.isEmpty())
        assertTrue(settings.sourceHealth.isEmpty())
        assertNull(settings.historyScan)
        assertNull(settings.lastCheckAt)
        assertTrue(settings.copy(localLockEnabled = true).localLockEnabled)
        for (id in listOf(Int.MIN_VALUE, 0, 2, Int.MAX_VALUE)) invalid { settings.copy(id = id) }
    }

    @Test fun `packs and source snapshots are unique and bounded to known sources`() {
        val settings = LocalSettingsEntity(localLockEnabled = true)
        assertEquals(5, PresetPack.entries.size)
        assertEquals(setOf("SCHOOL_AGE_HOUSEHOLD", "STUDENT", "SALARIED", "SELF_EMPLOYED", "CAREGIVING_RETIREMENT"), PresetPack.entries.map { it.name }.toSet())
        assertEquals(PresetPack.entries.toList(), settings.copy(selectedPacks = PresetPack.entries.toList()).selectedPacks)
        invalid { settings.copy(selectedPacks = listOf(PresetPack.STUDENT, PresetPack.STUDENT)) }
        invalid { settings.copy(sourceHealth = listOf(health(), health().copy(permission = SourcePermission.DENIED))) }
        val all = CaptureSource.entries.map { health(it) }
        assertEquals(3, settings.copy(sourceHealth = all).sourceHealth.size)
        invalid { settings.copy(sourceHealth = all + health()) }
        invalid { SettingsCodec.encodePacks(listOf(PresetPack.STUDENT, PresetPack.STUDENT)) }
        invalid { SettingsCodec.encodeSourceHealth(listOf(health(), health())) }
    }

    @Test fun `cached permission health is metadata not capture authority`() {
        val settings = LocalSettingsEntity(localLockEnabled = true, sourceHealth = listOf(health()))
        assertFalse(settings.liveSmsOptIn)
        assertEquals(CaptureState.NOT_ACTIVATED, CaptureAccess().state(health().source, health().permission))
        assertNull(settings.historyScan)
    }

    @Test fun `history progress validates duration ordering cursor and count at nanosecond boundaries`() {
        val progress = scan()
        assertNull(progress.lastProcessedSmsId)
        assertEquals(0L, progress.processedCount)
        assertFalse(progress.complete)
        assertEquals(now, progress.copy(fromReceivedAt = now).fromReceivedAt)
        invalid { progress.copy(fromReceivedAt = now.plusNanos(1)) }
        invalid { progress.copy(fromReceivedAt = progress.fromReceivedAt.minusNanos(1)) }
        for (cursor in listOf(Long.MIN_VALUE, -1L, 0L)) invalid { progress.copy(lastProcessedSmsId = cursor) }
        invalid { progress.copy(processedCount = -1) }
        assertEquals(Long.MAX_VALUE, progress.copy(lastProcessedSmsId = Long.MAX_VALUE, processedCount = Long.MAX_VALUE).processedCount)
    }

    @Test fun `settings JSON round trips typed lists progress and timestamps without precision loss`() {
        for (packs in listOf(emptyList(), PresetPack.entries.toList(), listOf(PresetPack.STUDENT))) {
            assertEquals(packs, SettingsCodec.decodePacks(SettingsCodec.encodePacks(packs)))
        }
        val snapshots = CaptureSource.entries.flatMap { source -> SourcePermission.entries.map { health(source).copy(permission = it) } }
        for (snapshot in snapshots) assertEquals(listOf(snapshot), SettingsCodec.decodeSourceHealth(SettingsCodec.encodeSourceHealth(listOf(snapshot))))
        assertEquals(emptyList<SourceHealthSnapshot>(), SettingsCodec.decodeSourceHealth(SettingsCodec.encodeSourceHealth(emptyList())))
        assertEquals(listOf(health().copy(lastCapturedAt = null)), SettingsCodec.decodeSourceHealth(SettingsCodec.encodeSourceHealth(listOf(health().copy(lastCapturedAt = null)))))
        for (progress in listOf(scan(), scan().copy(lastProcessedSmsId = 7, processedCount = 12, complete = true))) {
            assertEquals(progress, SettingsCodec.decodeHistoryScan(SettingsCodec.encodeHistoryScan(progress)))
        }
    }

    @Test fun `packs JSON rejects unknown codes arbitrary bodies wrong types and duplicates`() {
        val one = SettingsCodec.encodePacks(listOf(PresetPack.STUDENT))
        val duplicate = "[${one.removePrefix("[").removeSuffix("]")},${one.removePrefix("[").removeSuffix("]")}]"
        for (text in listOf("[\"SYNTHETIC_PRIVATE_MARKER\"]", "[1]", "[true]", "[null]", "{}", "null", "{", duplicate,
            "{\"rawMessage\":\"SYNTHETIC_PRIVATE_MARKER\"}")) bounded { SettingsCodec.decodePacks(text) }
    }

    @Test fun `health JSON rejects raw unknown fields invalid enums dates types and duplicate sources`() {
        val one = SettingsCodec.encodeSourceHealth(listOf(health()))
        val duplicate = "[${one.removePrefix("[").removeSuffix("]")},${one.removePrefix("[").removeSuffix("]")}]"
        val sourceValue = Regex("\\\"source\\\"\\s*:\\s*\\\"[^\\\"]*\\\"")
        val permissionValue = Regex("\\\"permission\\\"\\s*:\\s*\\\"[^\\\"]*\\\"")
        val invalid = listOf(one.replaceFirst("{", "{\"rawMessage\":\"SYNTHETIC_PRIVATE_MARKER\","),
            one.replace(sourceValue, "\"source\":\"SYNTHETIC_PRIVATE_MARKER\""),
            one.replace(permissionValue, "\"permission\":false"),
            one.replace(permissionValue, "\"permission\":\"SYNTHETIC_PRIVATE_MARKER\""),
            one.replace(now.toString(), "2026-02-30T12:34:56Z"),
            one.replace("\"$now\"", "123"), duplicate, "[null]", "{}", "null", "[true]")
        invalid.forEach { text ->
            assertNotEquals("Invalid JSON mutation must change the encoded fixture", one, text)
            bounded { SettingsCodec.decodeSourceHealth(text) }
        }
    }

    @Test fun `scan JSON rejects unknown fields wrong scalar types and invalid progress`() {
        val encoded = SettingsCodec.encodeHistoryScan(scan().copy(lastProcessedSmsId = 7, processedCount = 12))
        val cursor = Regex("\\\"lastProcessedSmsId\\\"\\s*:\\s*7")
        val count = Regex("\\\"processedCount\\\"\\s*:\\s*12")
        val complete = Regex("\\\"complete\\\"\\s*:\\s*false")
        val invalid = listOf(encoded.replaceFirst("{", "{\"sender\":\"SYNTHETIC_PRIVATE_MARKER\","),
            encoded.replace(cursor, "\"lastProcessedSmsId\":0"), encoded.replace(cursor, "\"lastProcessedSmsId\":7.5"),
            encoded.replace(cursor, "\"lastProcessedSmsId\":\"7\""), encoded.replace(count, "\"processedCount\":-1"),
            encoded.replace(count, "\"processedCount\":12.5"), encoded.replace(count, "\"processedCount\":\"12\""),
            encoded.replace(count, "\"processedCount\":9223372036854775808"),
            encoded.replace(cursor, "\"lastProcessedSmsId\":true"),
            encoded.replace(complete, "\"complete\":\"false\""),
            encoded.replace(now.toString(), "SYNTHETIC_PRIVATE_MARKER"),
            encoded.replace(scan().fromReceivedAt.toString(), now.plusNanos(1).toString()),
            encoded.replace(scan().fromReceivedAt.toString(), scan().fromReceivedAt.minusNanos(1).toString()),
            "{}", "[]", "null", "false")
        invalid.forEach { text ->
            assertNotEquals("Invalid JSON mutation must change the encoded fixture", encoded, text)
            bounded { SettingsCodec.decodeHistoryScan(text) }
        }
    }

    @Test fun `scope encoding includes kind and exact canonical UUID identity`() {
        val id = UUID.fromString("abcdefab-1234-4567-89ab-abcdefabcdef")
        for (kind in LedgerKind.entries) {
            val scope = LedgerKey(kind, id)
            val expected = if (kind == LedgerKind.PERSONAL) "personal:$id" else "household:$id"
            assertEquals(expected, converters.scopeToText(scope))
            assertEquals(scope, converters.textToScope(expected))
        }
        for (text in listOf("personal", "personal:1-1-1-1-1", "household:$id:extra", "PERSONAL:$id", " personal:$id", "personal:$id ", "personal:${id.toString().uppercase()}", "other:$id", "personal:SYNTHETIC_PRIVATE_MARKER")) {
            bounded { converters.textToScope(text) }
        }
    }

    @Test fun `budget markers contain only scoped identity month threshold and emitted time`() {
        val marker = BudgetAlertEntity(LedgerKey(LedgerKind.HOUSEHOLD, UUID(0, 1)), UUID(0, 2), YearMonth.of(2026, 11), 80, now)
        assertEquals(100, marker.copy(threshold = 100).threshold)
        for (threshold in listOf(Int.MIN_VALUE, -1, 0, 79, 81, 99, 101, Int.MAX_VALUE)) invalid { marker.copy(threshold = threshold) }
        invalid { marker.copy(month = YearMonth.of(0, 1)) }
        invalid { marker.copy(month = YearMonth.of(10000, 1)) }
    }
}
