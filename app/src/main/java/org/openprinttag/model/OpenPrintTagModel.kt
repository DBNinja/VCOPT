package org.openprinttag.model

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable
data class OpenPrintTagModel(
    var meta: MetaRegion? = MetaRegion(),
    var main: MainRegion = MainRegion(),
    var aux: AuxRegion? = AuxRegion(),

    // URL is usually a standard NDEF record, not CBOR
    var urlRecord: UrlRegion? = null
)



@Serializable
data class MetaRegion(
    @SerialName("0") var mainRegionOffset: Int? = null,
    @SerialName("1") var mainRegionSize: Int? = null,
    @SerialName("2") var auxRegionOffset: Int? = null,
    @SerialName("3") var auxRegionSize: Int? = null
)



// 1. Custom Serializer for LocalDate to Long (Epoch Seconds)
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeLong(value.atStartOfDay(ZoneOffset.UTC).toEpochSecond())
    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.ofEpochDay(decoder.decodeLong() / 86400) // Convert seconds to days
}

// 2. Custom Serializer for nullable LocalDate (for optional date fields)
object NullableLocalDateSerializer : KSerializer<LocalDate?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NullableLocalDate", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: LocalDate?) {
        if (value != null) encoder.encodeLong(value.atStartOfDay(ZoneOffset.UTC).toEpochSecond())
    }

    override fun deserialize(decoder: Decoder): LocalDate? {
        return try {
            LocalDate.ofEpochDay(decoder.decodeLong() / 86400)
        } catch (e: Exception) {
            null
        }
    }
}

// 3. Custom Serializer for String that can decode from CBOR integers
// CBOR stores enum values as integers, but we want String for UI compatibility
object IntOrStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IntOrString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String {
        return try {
            decoder.decodeString()
        } catch (e: Exception) {
            try {
                decoder.decodeInt().toString()
            } catch (e2: Exception) {
                try {
                    decoder.decodeLong().toString()
                } catch (e3: Exception) {
                    ""
                }
            }
        }
    }
}

// 4. Custom Serializer for nullable String that can decode from CBOR integers or bytes
object NullableIntOrStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NullableIntOrString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String? {
        // Try multiple CBOR types: string, int, long
        return try {
            decoder.decodeString()
        } catch (e: Exception) {
            try {
                decoder.decodeInt().toString()
            } catch (e2: Exception) {
                try {
                    decoder.decodeLong().toString()
                } catch (e3: Exception) {
                    null
                }
            }
        }
    }
}

// 5. Custom Serializer for List<String> that can decode from CBOR integer arrays
// CBOR stores tags/certs as integer IDs, but we want String list for UI compatibility
object IntListAsStringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = kotlinx.serialization.descriptors.listSerialDescriptor<String>()

    override fun serialize(encoder: Encoder, value: List<String>) {
        val composite = encoder.beginCollection(descriptor, value.size)
        value.forEachIndexed { index, item ->
            composite.encodeStringElement(descriptor, index, item)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): List<String> {
        val result = mutableListOf<String>()
        val composite = decoder.beginStructure(descriptor)
        while (true) {
            val index = composite.decodeElementIndex(descriptor)
            if (index == kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE) break
            // Try to decode as string first, fall back to int
            try {
                val value = composite.decodeStringElement(descriptor, index)
                result.add(value)
            } catch (e: Exception) {
                try {
                    val intValue = composite.decodeIntElement(descriptor, index)
                    result.add(intValue.toString())
                } catch (e2: Exception) {
                    // Skip undecodable elements
                }
            }
        }
        composite.endStructure(descriptor)
        return result
    }
}




/**
 * MainRegion contains all fields from the OpenPrintTag main_fields.yaml specification.
 * Field names follow the official spec naming convention.
 * CBOR keys are specified via @SerialName annotations.
 */
@Serializable
data class MainRegion(
    // === UUIDs (Keys 0-3) - stored as 16-byte binary in CBOR ===
    @SerialName("0") var instanceUuid: String? = null,
    @SerialName("1") var packageUuid: String? = null,
    @SerialName("2") var materialUuid: String? = null,
    @SerialName("3") var brandUuid: String? = null,

    // === GTIN (Key 4) ===
    @Serializable(with = NullableIntOrStringSerializer::class)
    @SerialName("4") var gtin: String? = null,

    // === Brand-Specific IDs (Keys 5-7) ===
    @SerialName("5") var brandSpecificInstanceId: String? = null,
    @SerialName("6") var brandSpecificPackageId: String? = null,
    @SerialName("7") var brandSpecificMaterialId: String? = null,

    // === Material Classification (Keys 8-11) ===
    @Serializable(with = IntOrStringSerializer::class)
    @SerialName("8") var materialClass: String = "FFF",
    @Serializable(with = NullableIntOrStringSerializer::class)
    @SerialName("9") var materialType: String? = null,
    @SerialName("10") var materialName: String? = null,
    @SerialName("11") var brandName: String? = null,  // renamed from 'brand'

    // === Write Protection (Key 13) - enum ===
    @Serializable(with = NullableIntOrStringSerializer::class)
    @SerialName("13") var writeProtection: String? = null,

    // === Dates (Keys 14, 15) ===
    @Serializable(with = NullableLocalDateSerializer::class)
    @SerialName("14") var manufacturedDate: LocalDate? = null,
    @Serializable(with = NullableLocalDateSerializer::class)
    @SerialName("15") var expirationDate: LocalDate? = null,

    // === Weights (Keys 16-18) - spec says "number" = Float ===
    @SerialName("16") var nominalNettoFullWeight: Float? = null,  // renamed from totalWeight, changed Int→Float
    @SerialName("17") var actualNettoFullWeight: Float? = null,   // renamed from ActTotalWeight, changed Int→Float
    @SerialName("18") var emptyContainerWeight: Float? = null,

    // === Colors (Keys 19-24) - stored as RGB(A) bytes in CBOR ===
    @SerialName("19") var primaryColor: String? = null,
    @SerialName("20") var secondaryColor0: String? = null,
    @SerialName("21") var secondaryColor1: String? = null,
    @SerialName("22") var secondaryColor2: String? = null,
    @SerialName("23") var secondaryColor3: String? = null,
    @SerialName("24") var secondaryColor4: String? = null,

    // === Optical Properties (Key 27) ===
    @SerialName("27") var transmissionDistance: Float? = null,

    // === Tags & Certifications (Keys 28, 56) - enum arrays ===
    @Serializable(with = IntListAsStringListSerializer::class)
    @SerialName("28") var materialTags: List<String> = emptyList(),
    @Serializable(with = IntListAsStringListSerializer::class)
    @SerialName("56") var certifications: List<String> = emptyList(),

    // === Physical Properties (Keys 29-33) ===
    @SerialName("29") var density: Float? = null,
    @SerialName("30") var filamentDiameter: Float? = null,  // KEY 30! (not deprecated key 12)
    @SerialName("31") var shoreHardnessA: Int? = null,
    @SerialName("32") var shoreHardnessD: Int? = null,
    @SerialName("33") var minNozzleDiameter: Float? = null,

    // === Temperatures (Keys 34-41) ===
    @SerialName("34") var minPrintTemp: Int? = null,
    @SerialName("35") var maxPrintTemp: Int? = null,
    @SerialName("36") var preheatTemp: Int? = null,
    @SerialName("37") var minBedTemp: Int? = null,
    @SerialName("38") var maxBedTemp: Int? = null,
    @SerialName("39") var minChamberTemp: Int? = null,
    @SerialName("40") var maxChamberTemp: Int? = null,
    @SerialName("41") var chamberTemperature: Int? = null,  // renamed from idealChamberTemp

    // === Container Dimensions (Keys 42-45) - FFF spool dimensions ===
    @SerialName("42") var containerWidth: Int? = null,
    @SerialName("43") var containerOuterDiameter: Int? = null,
    @SerialName("44") var containerInnerDiameter: Int? = null,
    @SerialName("45") var containerHoleDiameter: Int? = null,

    // === SLA-Specific Fields (Keys 46-51) ===
    @SerialName("46") var viscosity18c: Float? = null,
    @SerialName("47") var viscosity25c: Float? = null,
    @SerialName("48") var viscosity40c: Float? = null,
    @SerialName("49") var viscosity60c: Float? = null,
    @SerialName("50") var containerVolumetricCapacity: Float? = null,
    @SerialName("51") var cureWavelength: Int? = null,

    // === Material Abbreviation (Key 52) ===
    @SerialName("52") var materialAbbrev: String? = null,

    // === Filament Length (Keys 53-54) ===
    @SerialName("53") var nominalFullLength: Float? = null,
    @SerialName("54") var actualFullLength: Float? = null,

    // === Country of Origin (Key 55) ===
    @SerialName("55") var countryOfOrigin: String? = null,
)


/**
 * AuxRegion contains fields from the OpenPrintTag aux_fields.yaml specification.
 * These fields are typically user-writable even on protected tags.
 */
@Serializable
data class AuxRegion(
    @SerialName("0") var consumedWeight: Float? = null,  // changed Int→Float per spec
    @SerialName("1") var workgroup: String? = null,
    @SerialName("2") var generalPurposeRangeUser: String? = null,
    @Serializable(with = NullableLocalDateSerializer::class)
    @SerialName("3") var lastStirTime: LocalDate? = null,  // SLA-specific
)

@Serializable
data class UrlRegion(
    var url: String = "https://openprinttag.org",
    var includeInTag: Boolean = false
)



/**
 * Helper to get the Manufactured Date as Epoch Seconds for the Serializer
 */

fun getDateEpoch(date: LocalDate?): Long? {

    return date?.atStartOfDay(ZoneOffset.UTC)?.toEpochSecond()
}
