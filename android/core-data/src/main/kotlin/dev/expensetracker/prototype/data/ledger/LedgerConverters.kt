package dev.expensetracker.prototype.data.ledger

import androidx.room.TypeConverter
import dev.expensetracker.model.LedgerKey
import dev.expensetracker.model.LedgerKind
import dev.expensetracker.model.ReportingDates
import dev.expensetracker.model.TransactionKind
import dev.expensetracker.model.TransactionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.UUID

class LedgerConverters {
    private val instantFormat = DateTimeFormatterBuilder().appendInstant(9).toFormatter(Locale.ROOT)

    @TypeConverter fun packsToText(value: List<PresetPack>): String = SettingsCodec.encodePacks(value)
    @TypeConverter fun textToPacks(value: String): List<PresetPack> = SettingsCodec.decodePacks(value)
    @TypeConverter fun sourceHealthToText(value: List<SourceHealthSnapshot>): String = SettingsCodec.encodeSourceHealth(value)
    @TypeConverter fun textToSourceHealth(value: String): List<SourceHealthSnapshot> = SettingsCodec.decodeSourceHealth(value)
    @TypeConverter fun historyScanToText(value: HistoryScanProgress): String = SettingsCodec.encodeHistoryScan(value)
    @TypeConverter fun textToHistoryScan(value: String): HistoryScanProgress = SettingsCodec.decodeHistoryScan(value)

    @TypeConverter fun scopeToText(value: LedgerKey): String = "${value.kind.name.lowercase(Locale.ROOT)}:${value.id}"
    @TypeConverter fun textToScope(value: String): LedgerKey {
        val kind = when (value.substringBefore(':')) {
            "personal" -> LedgerKind.PERSONAL
            "household" -> LedgerKind.HOUSEHOLD
            else -> throw IllegalArgumentException("INVALID_LEDGER_SCOPE")
        }
        val id = textToUuid(value.substringAfter(':', ""))
        return LedgerKey(kind, id).also { require(scopeToText(it) == value) { "INVALID_LEDGER_SCOPE" } }
    }

    @TypeConverter fun undoToText(value: CorrectionUndoPayload): String = CorrectionUndoCodec.encode(value)
    @TypeConverter fun textToUndo(value: String): CorrectionUndoPayload = CorrectionUndoCodec.decode(value)

    @TypeConverter fun uuidToText(value: UUID): String = value.toString()
    @TypeConverter fun textToUuid(value: String): UUID {
        val parsed = try { UUID.fromString(value) } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("INVALID_UUID")
        }
        require(parsed.toString() == value) { "INVALID_UUID" }
        return parsed
    }

    @TypeConverter fun dateToText(value: LocalDate): String = value.toString().also { ReportingDates.parseDate(it) }
    @TypeConverter fun textToDate(value: String): LocalDate = ReportingDates.parseDate(value)
    @TypeConverter fun monthToText(value: YearMonth): String = value.toString().also { ReportingDates.parseMonth(it) }
    @TypeConverter fun textToMonth(value: String): YearMonth = ReportingDates.parseMonth(value)
    @TypeConverter fun instantToText(value: Instant): String = instantFormat.format(value).also { ReportingDates.parseUtcInstant(it) }
    @TypeConverter fun textToInstant(value: String): Instant = ReportingDates.parseUtcInstant(value)

    @TypeConverter fun kindToText(value: TransactionKind): String = value.name.lowercase(Locale.ROOT)
    @TypeConverter fun textToKind(value: String): TransactionKind = enumCode(value)
    @TypeConverter fun statusToText(value: TransactionStatus): String = value.name.lowercase(Locale.ROOT)
    @TypeConverter fun textToStatus(value: String): TransactionStatus = enumCode(value)
    @TypeConverter fun sourceToText(value: ObservationSource): String = value.name.lowercase(Locale.ROOT)
    @TypeConverter fun textToSource(value: String): ObservationSource = enumCode(value)
    @TypeConverter fun outcomeToText(value: ObservationOutcome): String = value.name.lowercase(Locale.ROOT)
    @TypeConverter fun textToOutcome(value: String): ObservationOutcome = enumCode(value)

    private inline fun <reified T : Enum<T>> enumCode(value: String): T = enumValues<T>()
        .firstOrNull { it.name.lowercase(Locale.ROOT) == value }
        ?: throw IllegalArgumentException("INVALID_ENUM_CODE")
}
