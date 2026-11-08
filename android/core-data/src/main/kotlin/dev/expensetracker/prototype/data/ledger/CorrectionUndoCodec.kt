@file:kotlinx.serialization.UseSerializers(UndoUuidSerializer::class, UndoInstantSerializer::class, UndoVersionSerializer::class)

package dev.expensetracker.prototype.data.ledger

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class TransactionCategoryUndo(
    val transactionId: UUID,
    val previousCategoryId: UUID?,
    val postEditVersion: Long,
) {
    init { require(postEditVersion > 0) { "INVALID_UNDO_VERSION" } }
}

@Serializable
data class MerchantRuleSnapshot(
    val id: UUID,
    val merchantKey: String,
    val accountId: UUID?,
    val categoryId: UUID,
    val updatedAt: Instant,
) {
    init { requireMerchantKey(merchantKey) }
}

@Serializable
data class CorrectionUndoPayload(
    val transactions: List<TransactionCategoryUndo>,
    val priorRule: MerchantRuleSnapshot? = null,
    val postRule: MerchantRuleSnapshot? = null,
) {
    init { validate() }

    internal fun validate() {
        require(transactions.isNotEmpty() || priorRule != null || postRule != null) { "EMPTY_UNDO" }
        require(transactions.map { it.transactionId }.toSet().size == transactions.size) { "DUPLICATE_UNDO_TRANSACTION" }
        transactions.forEach { require(it.postEditVersion > 0) { "INVALID_UNDO_VERSION" } }
        listOfNotNull(priorRule, postRule).forEach { requireMerchantKey(it.merchantKey) }
        if (priorRule != null && postRule != null) {
            require(priorRule.id == postRule.id && priorRule.merchantKey == postRule.merchantKey &&
                priorRule.accountId == postRule.accountId) { "UNDO_RULE_SCOPE_MISMATCH" }
        }
    }
}

/** Only this structured inverse-change format is persisted; never arbitrary JSON or alert text. */
object CorrectionUndoCodec {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        encodeDefaults = true
    }

    fun encode(payload: CorrectionUndoPayload): String = bounded {
        payload.validate()
        json.encodeToString(CorrectionUndoPayload.serializer(), payload)
    }

    fun decode(text: String): CorrectionUndoPayload = bounded {
        json.decodeFromString(CorrectionUndoPayload.serializer(), text).also { it.validate() }
    }

    // Serialization/parser exceptions can include source excerpts. Never retain them as causes.
    private inline fun <T> bounded(block: () -> T): T = try {
        block()
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("INVALID_UNDO_PAYLOAD")
    } catch (_: java.time.DateTimeException) {
        throw IllegalArgumentException("INVALID_UNDO_PAYLOAD")
    }
}

internal object UndoUuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UndoUUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = LedgerConverters().textToUuid(strictString(decoder))
}

internal object UndoInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("UndoInstant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(LedgerConverters().instantToText(value))
    override fun deserialize(decoder: Decoder): Instant = LedgerConverters().textToInstant(strictString(decoder))
}

internal object UndoVersionSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("UndoVersion", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
    override fun deserialize(decoder: Decoder): Long {
        val primitive = (decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive
            ?: throw IllegalArgumentException("INVALID_UNDO_VERSION")
        require(!primitive.isString && Regex("[1-9][0-9]*").matches(primitive.content)) { "INVALID_UNDO_VERSION" }
        return primitive.content.toLongOrNull() ?: throw IllegalArgumentException("INVALID_UNDO_VERSION")
    }
}

private fun strictString(decoder: Decoder): String {
    val primitive = (decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive
        ?: throw IllegalArgumentException("INVALID_UNDO_VALUE")
    require(primitive.isString) { "INVALID_UNDO_VALUE" }
    return primitive.content
}
