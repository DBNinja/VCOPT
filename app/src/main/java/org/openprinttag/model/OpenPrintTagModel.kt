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

/*
data class OpenPrintTagModel(
    var materialClass: String = "FFF",
    var materialType: String? = "PLA",
    var brand: String? = "",
    var materialName: String? = "",
    var primaryColor: String? = "",
    var density: Float? = null,
    var gtin: Int? = null,
    var manufacturedDate: LocalDate? = null,
    var countryOfOrigin: String? = null,
    var minPrintTemp: Int? = null,
    var maxPrintTemp: Int? = null,
    var preheatTemp: Int? = null,
    var minBedTemp: Int? = null,
    var maxBedTemp: Int? = null,
    var materialTags: List<String> = emptyList(),
    var certifications: List<String> = emptyList(),
    var includeUrlRecord: Boolean = false,
    var tagUrl: String? = null
)*/



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




@Serializable
data class MainRegion(
    // Enums are stored as their Integer keys in the binary tag
    // but we keep them as Strings here for UI/Spinner compatibility.
    // The Serializer will handle the mapping to Integers.
    @SerialName("8") var materialClass: String = "FFF",
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
    @SerialName("4") var gtin: String? = null,
    
    // Key 12: Nominal Diameter (mm) - Important for FFF
    @SerialName("12") var nominalDiameter: Float? = null,

    // Key 16: Total Weight / Net Weight (g)
    @SerialName("16") var totalWeight: int? = null,
    @SerialName("17") var ActTotalWeight: int? = null,
    
    
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

    // Multi-select enums
    @SerialName("28") var materialTags: List<String> = emptyList(),
    @SerialName("56") var certifications: List<String> = emptyList(),
    )


    @kotlinx.serialization.Serializable
    data class AuxRegion(
        @SerialName("0") var consumedWeight: int? = null,
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

