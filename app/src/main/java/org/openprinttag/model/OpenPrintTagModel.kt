package org.openprinttag.model

import java.time.LocalDate

data class OpenPrintTagModel(
    var materialClass: String = "FFF",
    var materialType: String = "PLA",
    var brand: String = "",
    var materialName: String = "",
    var primaryColor: String = "",
    var density: Float? = null,
    var gtin: String? = null,
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
)
