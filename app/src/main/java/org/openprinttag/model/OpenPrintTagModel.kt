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

// 2. Custom Serializer for String that can decode from CBOR integers
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

// 3. Custom Serializer for nullable String that can decode from CBOR integers or bytes
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

// 4. Custom Serializer for List<String> that can decode from CBOR integer arrays
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




@Serializable
data class MainRegion(
    // Enums are stored as their Integer keys in the binary tag
    // but we keep them as Strings here for UI/Spinner compatibility.
    // The Serializer will handle the mapping to Integers.
    @Serializable(with = IntOrStringSerializer::class)
    @SerialName("8") var materialClass: String = "FFF",
    @Serializable(with = NullableIntOrStringSerializer::class)
    @SerialName("9") var materialType: String? = null,

    @SerialName("11") var brand: String? = null,
    @SerialName("10") var materialName: String? = null,
    @SerialName("52") var materialAbbrev: String? = null,

    // Key 19: Primary Color (ARGB or RGB)
    @SerialName("19") var primaryColor: String? = null,
    @SerialName("20") var secondary_color_0: String? = null,
    @SerialName("21") var secondary_color_1: String? = null,
    @SerialName("22") var secondary_color_2: String? = null,
    @SerialName("23") var secondary_color_3: String? = null,
    @SerialName("24") var secondary_color_4: String? = null,

    // Key 29: Density is a float (g/cm³)
    @SerialName("27") var transmission_distance: Float? = null,
    @SerialName("29") var density: Float? = null,

    // Key 4: GTIN should be treated as a number in the binary
    @Serializable(with = NullableIntOrStringSerializer::class)
    @SerialName("4") var gtin: String? = null,

    // Key 12: Nominal Diameter (mm) - Important for FFF
    @SerialName("12") var nominalDiameter: Float? = null,

    // Key 16: Total Weight / Net Weight (g)
    @SerialName("16") var totalWeight: Int? = null,
    @SerialName("17") var ActTotalWeight: Int? = null,
    
    
    // Key 14: Date. CBOR expects epoch seconds
    // Use @Serializable(with = ...) for LocalDate
    @kotlinx.serialization.Serializable(with = LocalDateSerializer::class)
    @SerialName("14") var manufacturedDate: LocalDate? = null,
    
    @SerialName("55") var countryOfOrigin: String? = null,

    // Keys 34-38: Temperatures are Integers (°C)
    @SerialName("34") var minPrintTemp: Int? = null,
    @SerialName("35") var maxPrintTemp: Int? = null,
    @SerialName("36") var preheatTemp: Int? = null,
    @SerialName("37") var minBedTemp: Int? = null,
    @SerialName("38") var maxBedTemp: Int? = null,
    @SerialName("39") var minChamberTemp: Int? = null,
    @SerialName("40") var maxChamberTemp: Int? = null,
    @SerialName("41") var idealChamberTemp: Int? = null,

    // Multi-select enums (CBOR stores as integer arrays, we convert to strings)
    @Serializable(with = IntListAsStringListSerializer::class)
    @SerialName("28") var materialTags: List<String> = emptyList(),
    @Serializable(with = IntListAsStringListSerializer::class)
    @SerialName("56") var certifications: List<String> = emptyList(),
    )


    @kotlinx.serialization.Serializable
    data class AuxRegion(
        @SerialName("0") var consumedWeight: Int? = null,
        @SerialName("1") var workgroup: String? = ""
        // ... move other aux_fields.yaml keys here
    )

    @kotlinx.serialization.Serializable
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

