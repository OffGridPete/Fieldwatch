package app.fieldwatch.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * A rule this phone can apply stays a [MatchRule]. A rule whose kind or radio
 * token is not BLE or Wi-Fi is kept as the original object so a later export
 * still contains it, and so a match-all signature cannot succeed by dropping it.
 */
internal object MatchRuleSerializer : KSerializer<MatchRule> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MatchRule")

    override fun deserialize(decoder: Decoder): MatchRule {
        val input = decoder as? JsonDecoder
            ?: error("A signature rule has to be JSON")
        val element = input.decodeJsonElement()
        val obj = element as? JsonObject ?: return foreign(element)
        if (!knownRule(obj)) {
            return MatchRule(
                kind = RuleKind.NAME_CONTAINS,
                enabled = enabledFlag(obj),
                foreign = obj,
            )
        }
        val wire = input.json.decodeFromJsonElement(MatchRuleWire.serializer(), obj)
        return MatchRule(
            kind = RuleKind.valueOf(wire.kind),
            text = wire.text,
            companyId = wire.companyId,
            dataPrefixHex = wire.dataPrefixHex,
            radio = wire.radio?.let { RadioKind.valueOf(it) },
            enabled = wire.enabled,
        )
    }

    override fun serialize(encoder: Encoder, value: MatchRule) {
        val output = encoder as? JsonEncoder
            ?: error("A signature rule has to be JSON")
        val foreign = value.foreign
        if (foreign != null) {
            output.encodeJsonElement(foreign)
            return
        }
        output.encodeSerializableValue(
            MatchRuleWire.serializer(),
            MatchRuleWire(
                kind = value.kind.name,
                text = value.text,
                companyId = value.companyId,
                dataPrefixHex = value.dataPrefixHex,
                radio = value.radio?.name,
                enabled = value.enabled,
            ),
        )
    }

    private fun foreign(element: JsonElement): MatchRule = MatchRule(
        kind = RuleKind.NAME_CONTAINS,
        foreign = JsonObject(mapOf("kind" to element)),
    )

    private fun knownRule(obj: JsonObject): Boolean {
        val kind = obj["kind"]
        val kindToken = (kind as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (kindToken == null || RuleKind.entries.none { it.name == kindToken }) return false
        val radio = obj["radio"] ?: return true
        if (radio is JsonNull) return true
        val token = (radio as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return false
        return token == RadioKind.BLE.name || token == RadioKind.WIFI.name
    }

    /** A disabled unknown rule must not block a match-all signature. */
    private fun enabledFlag(obj: JsonObject): Boolean {
        val flag = obj["enabled"] as? JsonPrimitive ?: return true
        return flag.content != "false"
    }
}

@Serializable
private data class MatchRuleWire(
    val kind: String,
    val text: String = "",
    val companyId: Int = 0,
    val dataPrefixHex: String = "",
    val radio: String? = null,
    val enabled: Boolean = true,
)

/**
 * One bad field (unknown type, endian, or gate) is omitted.
 * The signature still matches. The other fields stay.
 */
internal object DecodeFieldListSerializer : KSerializer<List<DecodeField>> {
    private val delegate = ListSerializer(DecodeField.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<DecodeField>) {
        encoder.encodeSerializableValue(delegate, value)
    }

    override fun deserialize(decoder: Decoder): List<DecodeField> {
        val input = decoder as? JsonDecoder ?: return decoder.decodeSerializableValue(delegate)
        val element = input.decodeJsonElement()
        val array = element as? JsonArray ?: return emptyList()
        val kept = ArrayList<DecodeField>(array.size)
        for (item in array) {
            val field = runCatching {
                input.json.decodeFromJsonElement(DecodeField.serializer(), item)
            }.getOrNull()
            if (field != null) kept += field
        }
        return kept
    }
}
