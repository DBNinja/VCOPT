package org.vcoprinttag.model

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
    @OptIn(ExperimentalSerializationApi::class)
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
 * Field names follow the official spec naming convention (snake_case).
 * CBOR keys are specified via @SerialName annotations.
 */
@Serializable
data class MainRegion(
    // === UUIDs (Keys 0-3) - stored as 16-byte binary in CBOR ===
    @SerialName("0") var instance_uuid: String? = null,
    @SerialName("1") var package_uuid: String? = null,
    @SerialName("2") var material_uuid: String? = null,
    @SerialName("3") var brand_uuid: String? = null,

    // === GTIN (Key 4) ===
    @Serializable(with = NullableIntOrStringSerializer::class)
    @SerialName("4") var gtin: String? = null,

    // === Brand-Specific IDs (Keys 5-7) ===
    @SerialName("5") var brand_specific_instance_id: String? = null,
    @SerialName("6") var brand_specific_package_id: String? = null,
    @SerialName("7") var brand_specific_material_id: String? = null,

    // === Material Classification (Keys 8-11) ===
    @Serializable(with = IntOrStringSerializer::class)
    @SerialName("8") var material_class: String = "FFF",
    @Serializable(with = NullableIntOrStringSerializer::class)
    @SerialName("9") var material_type: String? = null,
    @SerialName("10") var material_name: String? = null,
    @SerialName("11") var brand_name: String? = null,

    // === Write Protection (Key 13) - enum ===
    @Serializable(with = NullableIntOrStringSerializer::class)
    @SerialName("13") var write_protection: String? = null,

    // === Dates (Keys 14, 15) ===
    @Serializable(with = NullableLocalDateSerializer::class)
    @SerialName("14") var manufactured_date: LocalDate? = null,
    @Serializable(with = NullableLocalDateSerializer::class)
    @SerialName("15") var expiration_date: LocalDate? = null,

    // === Weights (Keys 16-18) - spec says "number" = Float ===
    @SerialName("16") var nominal_netto_full_weight: Float? = null,
    @SerialName("17") var actual_netto_full_weight: Float? = null,
    @SerialName("18") var empty_container_weight: Float? = null,

    // === Colors (Keys 19-24) - stored as RGB(A) bytes in CBOR ===
    @SerialName("19") var primary_color: String? = null,
    @SerialName("20") var secondary_color_0: String? = null,
    @SerialName("21") var secondary_color_1: String? = null,
    @SerialName("22") var secondary_color_2: String? = null,
    @SerialName("23") var secondary_color_3: String? = null,
    @SerialName("24") var secondary_color_4: String? = null,

    // === Optical Properties (Key 27) ===
    @SerialName("27") var transmission_distance: Float? = null,

    // === Tags & Certifications (Keys 28, 56) - enum arrays ===
    @Serializable(with = IntListAsStringListSerializer::class)
    @SerialName("28") var tags: List<String> = emptyList(),
    @Serializable(with = IntListAsStringListSerializer::class)
    @SerialName("56") var certifications: List<String> = emptyList(),

    // === Physical Properties (Keys 29-33) ===
    @SerialName("29") var density: Float? = null,
    @SerialName("30") var filament_diameter: Float? = null,  // KEY 30! (not deprecated key 12)
    @SerialName("31") var shore_hardness_a: Int? = null,
    @SerialName("32") var shore_hardness_d: Int? = null,
    @SerialName("33") var min_nozzle_diameter: Float? = null,

    // === Temperatures (Keys 34-41) ===
    @SerialName("34") var min_print_temperature: Int? = null,
    @SerialName("35") var max_print_temperature: Int? = null,
    @SerialName("36") var preheat_temperature: Int? = null,
    @SerialName("37") var min_bed_temperature: Int? = null,
    @SerialName("38") var max_bed_temperature: Int? = null,
    @SerialName("39") var min_chamber_temperature: Int? = null,
    @SerialName("40") var max_chamber_temperature: Int? = null,
    @SerialName("41") var chamber_temperature: Int? = null,

    // === Container Dimensions (Keys 42-45) - FFF spool dimensions ===
    @SerialName("42") var container_width: Int? = null,
    @SerialName("43") var container_outer_diameter: Int? = null,
    @SerialName("44") var container_inner_diameter: Int? = null,
    @SerialName("45") var container_hole_diameter: Int? = null,

    // === SLA-Specific Fields (Keys 46-51) ===
    @SerialName("46") var viscosity_18c: Float? = null,
    @SerialName("47") var viscosity_25c: Float? = null,
    @SerialName("48") var viscosity_40c: Float? = null,
    @SerialName("49") var viscosity_60c: Float? = null,
    @SerialName("50") var container_volumetric_capacity: Float? = null,
    @SerialName("51") var cure_wavelength: Int? = null,

    // === Material Abbreviation (Key 52) ===
    @SerialName("52") var material_abbreviation: String? = null,

    // === Filament Length (Keys 53-54) ===
    @SerialName("53") var nominal_full_length: Float? = null,
    @SerialName("54") var actual_full_length: Float? = null,

    // === Country of Origin (Key 55) ===
    @SerialName("55") var country_of_origin: String? = null,
)


/**
 * AuxRegion contains fields from the OpenPrintTag aux_fields.yaml specification.
 * These fields are typically user-writable even on protected tags.
 */
@Serializable
data class AuxRegion(
    @SerialName("0") var consumed_weight: Float? = null,
    @SerialName("1") var workgroup: String? = null,
    @SerialName("2") var general_purpose_range_user: String? = null,
    @Serializable(with = NullableLocalDateSerializer::class)
    @SerialName("3") var last_stir_time: LocalDate? = null,
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
