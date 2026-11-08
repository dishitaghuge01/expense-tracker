package dev.expensetracker.prototype.data.ledger

import dev.expensetracker.model.CaptureSource
import dev.expensetracker.model.SourcePermission
import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/** Closed typed JSON formats only; these are not arbitrary settings or alert payload storage. */
object SettingsCodec {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        encodeDefaults = true
    }
    private val packsSerializer = ListSerializer(PresetPack.serializer())
    private val healthSerializer = ListSerializer(SourceHealthSnapshot.serializer())

    fun encodePacks(packs: List<PresetPack>): String = bounded {
        requirePacks(packs)
        json.encodeToString(packsSerializer, packs)
    }

    fun decodePacks(text: String): List<PresetPack> = bounded {
        json.decodeFromString(packsSerializer, text).also(::requirePacks)
    }

    fun encodeSourceHealth(health: List<SourceHealthSnapshot>): String = bounded {
        requireSourceHealth(health)
        json.encodeToString(healthSerializer, health)
    }

    fun decodeSourceHealth(text: String): List<SourceHealthSnapshot> = bounded {
        json.decodeFromString(healthSerializer, text).also(::requireSourceHealth)
    }

    fun encodeHistoryScan(progress: HistoryScanProgress): String = bounded {
        json.encodeToString(HistoryScanProgress.serializer(), progress)
    }

    fun decodeHistoryScan(text: String): HistoryScanProgress = bounded {
        json.decodeFromString(HistoryScanProgress.serializer(), text)
    }

    // Parser errors may contain source excerpts. Deliberately discard both message and cause.
    private inline fun <T> bounded(block: () -> T): T = try {
        block()
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("INVALID_SETTINGS_PAYLOAD")
    } catch (_: java.time.DateTimeException) {
        throw IllegalArgumentException("INVALID_SETTINGS_PAYLOAD")
    }
}

internal abstract class SettingsEnumSerializer<T : Enum<T>>(name: String, private val values: Array<T>) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): T {
        val text = settingsString(decoder)
        return values.firstOrNull { it.name == text } ?: throw IllegalArgumentException("INVALID_SETTINGS_CODE")
    }
}

internal object PresetPackSerializer : SettingsEnumSerializer<PresetPack>("PresetPack", PresetPack.entries.toTypedArray())
internal object SettingsSourceSerializer : SettingsEnumSerializer<CaptureSource>("CaptureSource", CaptureSource.entries.toTypedArray())
internal object SettingsPermissionSerializer : SettingsEnumSerializer<SourcePermission>("SourcePermission", SourcePermission.entries.toTypedArray())

internal object SettingsInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("SettingsInstant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(LedgerConverters().instantToText(value))
    override fun deserialize(decoder: Decoder): Instant = LedgerConverters().textToInstant(settingsString(decoder))
}

internal object SettingsLongSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("SettingsLong", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
    override fun deserialize(decoder: Decoder): Long {
        val primitive = settingsPrimitive(decoder)
        require(!primitive.isString && Regex("-?(0|[1-9][0-9]*)").matches(primitive.content)) { "INVALID_SETTINGS_INTEGER" }
        return primitive.content.toLongOrNull() ?: throw IllegalArgumentException("INVALID_SETTINGS_INTEGER")
    }
}

internal object SettingsBooleanSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("SettingsBoolean", PrimitiveKind.BOOLEAN)
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
    override fun deserialize(decoder: Decoder): Boolean {
        val primitive = settingsPrimitive(decoder)
        require(!primitive.isString) { "INVALID_SETTINGS_BOOLEAN" }
        return when (primitive.content) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("INVALID_SETTINGS_BOOLEAN")
        }
    }
}

private fun settingsPrimitive(decoder: Decoder): JsonPrimitive =
    (decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive
        ?: throw IllegalArgumentException("INVALID_SETTINGS_VALUE")

private fun settingsString(decoder: Decoder): String = settingsPrimitive(decoder).also {
    require(it.isString) { "INVALID_SETTINGS_VALUE" }
}.content
