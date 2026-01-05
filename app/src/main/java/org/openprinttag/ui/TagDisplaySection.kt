package org.openprinttag.ui

import org.openprinttag.model.OpenPrintTagModel

/**
 * Sealed class representing different types of display sections for tag data.
 * Each section has its own ViewHolder type in the adapter.
 */
sealed class TagDisplayItem {

    /**
     * Material header showing brand, name, type, and colors
     */
    data class MaterialHeader(
        val brandName: String?,
        val materialName: String?,
        val materialType: String?,
        val materialClass: String,
        val primaryColor: String?,
        val secondaryColors: List<String>
    ) : TagDisplayItem()

    /**
     * Section header (e.g., "Physical Properties", "Temperatures")
     */
    data class SectionHeader(
        val title: String
    ) : TagDisplayItem()

    /**
     * Single property row with label and value
     */
    data class PropertyRow(
        val label: String,
        val value: String,
        val secondaryValue: String? = null // For "actual" values
    ) : TagDisplayItem()

    /**
     * Temperature display with range
     */
    data class TemperatureRow(
        val label: String,
        val minTemp: Int?,
        val maxTemp: Int?,
        val singleValue: Int? = null
    ) : TagDisplayItem()

    /**
     * Usage tracking with progress bar
     */
    data class UsageProgress(
        val totalWeight: Float?,
        val consumedWeight: Float?,
        val workgroup: String?
    ) : TagDisplayItem()

    /**
     * Color swatches row
     */
    data class ColorSwatches(
        val colors: List<String>
    ) : TagDisplayItem()

    /**
     * Chips for tags or certifications
     */
    data class ChipGroup(
        val items: List<String>,
        val isOutlined: Boolean = false
    ) : TagDisplayItem()

    /**
     * Empty state when no data is available
     */
    data class EmptyState(
        val message: String
    ) : TagDisplayItem()
}

/**
 * Builds the list of display items from an OpenPrintTagModel.
 * Uses material-class-aware logic to show relevant fields.
 */
object TagDisplayBuilder {

    fun buildDisplayItems(model: OpenPrintTagModel?): List<TagDisplayItem> {
        if (model == null) {
            return listOf(TagDisplayItem.EmptyState("No tag data available"))
        }

        val items = mutableListOf<TagDisplayItem>()
        val main = model.main
        val aux = model.aux
        val materialClass = main.materialClass

        // 1. Material Header (always shown)
        val secondaryColors = listOfNotNull(
            main.secondaryColor0,
            main.secondaryColor1,
            main.secondaryColor2,
            main.secondaryColor3,
            main.secondaryColor4
        )

        items.add(TagDisplayItem.MaterialHeader(
            brandName = main.brandName,
            materialName = main.materialName,
            materialType = main.materialType,
            materialClass = materialClass,
            primaryColor = main.primaryColor,
            secondaryColors = secondaryColors
        ))

        // 2. Physical Properties
        val physicalProps = buildPhysicalProperties(main, materialClass)
        if (physicalProps.isNotEmpty()) {
            items.add(TagDisplayItem.SectionHeader("Physical Properties"))
            items.addAll(physicalProps)
        }

        // 3. Temperatures (for FFF)
        if (materialClass == "FFF") {
            val tempProps = buildTemperatureProperties(main)
            if (tempProps.isNotEmpty()) {
                items.add(TagDisplayItem.SectionHeader("Temperatures"))
                items.addAll(tempProps)
            }
        }

        // 4. SLA Properties (only for SLA)
        if (materialClass == "SLA") {
            val slaProps = buildSlaProperties(main)
            if (slaProps.isNotEmpty()) {
                items.add(TagDisplayItem.SectionHeader("SLA Properties"))
                items.addAll(slaProps)
            }
        }

        // 5. Usage Tracking (from AuxRegion)
        if (aux != null && (aux.consumedWeight != null || main.nominalNettoFullWeight != null)) {
            items.add(TagDisplayItem.SectionHeader("Usage"))
            items.add(TagDisplayItem.UsageProgress(
                totalWeight = main.nominalNettoFullWeight,
                consumedWeight = aux.consumedWeight,
                workgroup = aux.workgroup
            ))
        }

        // 6. Spool Dimensions (FFF with container data)
        if (materialClass == "FFF") {
            val spoolProps = buildSpoolProperties(main)
            if (spoolProps.isNotEmpty()) {
                items.add(TagDisplayItem.SectionHeader("Spool Dimensions"))
                items.addAll(spoolProps)
            }
        }

        // 7. Tags & Certifications
        if (main.materialTags.isNotEmpty()) {
            items.add(TagDisplayItem.SectionHeader("Tags"))
            items.add(TagDisplayItem.ChipGroup(main.materialTags, isOutlined = false))
        }

        if (main.certifications.isNotEmpty()) {
            items.add(TagDisplayItem.SectionHeader("Certifications"))
            items.add(TagDisplayItem.ChipGroup(main.certifications, isOutlined = true))
        }

        // 8. Metadata
        val metadataProps = buildMetadataProperties(main)
        if (metadataProps.isNotEmpty()) {
            items.add(TagDisplayItem.SectionHeader("Details"))
            items.addAll(metadataProps)
        }

        return items
    }

    private fun buildPhysicalProperties(main: org.openprinttag.model.MainRegion, materialClass: String): List<TagDisplayItem> {
        val props = mutableListOf<TagDisplayItem>()

        // Filament Diameter (FFF)
        main.filamentDiameter?.let {
            props.add(TagDisplayItem.PropertyRow("Diameter", "${it}mm"))
        }

        // Weight with actual comparison
        if (main.nominalNettoFullWeight != null || main.actualNettoFullWeight != null) {
            val nominal = main.nominalNettoFullWeight?.let { "${it.toInt()}g" } ?: "N/A"
            val actual = main.actualNettoFullWeight?.let { "(actual: ${it.toInt()}g)" }
            props.add(TagDisplayItem.PropertyRow("Weight", nominal, actual))
        }

        // Density
        main.density?.let {
            props.add(TagDisplayItem.PropertyRow("Density", "${it} g/cm³"))
        }

        // Length with actual comparison
        if (main.nominalFullLength != null || main.actualFullLength != null) {
            val nominal = main.nominalFullLength?.let { "${it}m" } ?: "N/A"
            val actual = main.actualFullLength?.let { "(actual: ${it}m)" }
            props.add(TagDisplayItem.PropertyRow("Length", nominal, actual))
        }

        // Shore Hardness (for TPU and other flexible materials)
        if (main.shoreHardnessA != null || main.shoreHardnessD != null) {
            val hardness = buildString {
                main.shoreHardnessA?.let { append("${it}A") }
                if (main.shoreHardnessA != null && main.shoreHardnessD != null) append(" / ")
                main.shoreHardnessD?.let { append("${it}D") }
            }
            props.add(TagDisplayItem.PropertyRow("Shore Hardness", hardness))
        }

        // Min Nozzle Diameter
        main.minNozzleDiameter?.let {
            props.add(TagDisplayItem.PropertyRow("Min Nozzle", "${it}mm"))
        }

        return props
    }

    private fun buildTemperatureProperties(main: org.openprinttag.model.MainRegion): List<TagDisplayItem> {
        val props = mutableListOf<TagDisplayItem>()

        // Print Temperature Range
        if (main.minPrintTemp != null || main.maxPrintTemp != null) {
            props.add(TagDisplayItem.TemperatureRow("Print", main.minPrintTemp, main.maxPrintTemp))
        }

        // Bed Temperature Range
        if (main.minBedTemp != null || main.maxBedTemp != null) {
            props.add(TagDisplayItem.TemperatureRow("Bed", main.minBedTemp, main.maxBedTemp))
        }

        // Chamber Temperature
        if (main.minChamberTemp != null || main.maxChamberTemp != null) {
            props.add(TagDisplayItem.TemperatureRow("Chamber", main.minChamberTemp, main.maxChamberTemp))
        } else if (main.chamberTemperature != null) {
            props.add(TagDisplayItem.TemperatureRow("Chamber", null, null, main.chamberTemperature))
        }

        // Preheat Temperature
        main.preheatTemp?.let {
            props.add(TagDisplayItem.TemperatureRow("Preheat", null, null, it))
        }

        return props
    }

    private fun buildSlaProperties(main: org.openprinttag.model.MainRegion): List<TagDisplayItem> {
        val props = mutableListOf<TagDisplayItem>()

        // Viscosity at different temperatures
        main.viscosity25c?.let {
            props.add(TagDisplayItem.PropertyRow("Viscosity @ 25°C", "${it} cP"))
        }

        // Cure Wavelength
        main.cureWavelength?.let {
            props.add(TagDisplayItem.PropertyRow("Cure Wavelength", "${it}nm"))
        }

        // Container Volume
        main.containerVolumetricCapacity?.let {
            props.add(TagDisplayItem.PropertyRow("Container Volume", "${it}ml"))
        }

        return props
    }

    private fun buildSpoolProperties(main: org.openprinttag.model.MainRegion): List<TagDisplayItem> {
        val props = mutableListOf<TagDisplayItem>()

        // Spool dimensions
        main.containerOuterDiameter?.let {
            props.add(TagDisplayItem.PropertyRow("Outer Diameter", "${it}mm"))
        }

        main.containerWidth?.let {
            props.add(TagDisplayItem.PropertyRow("Width", "${it}mm"))
        }

        main.containerHoleDiameter?.let {
            props.add(TagDisplayItem.PropertyRow("Hole Diameter", "${it}mm"))
        }

        return props
    }

    private fun buildMetadataProperties(main: org.openprinttag.model.MainRegion): List<TagDisplayItem> {
        val props = mutableListOf<TagDisplayItem>()

        // GTIN
        main.gtin?.let {
            props.add(TagDisplayItem.PropertyRow("GTIN", it))
        }

        // Country of Origin
        main.countryOfOrigin?.let {
            props.add(TagDisplayItem.PropertyRow("Country", it))
        }

        // Manufactured Date
        main.manufacturedDate?.let {
            props.add(TagDisplayItem.PropertyRow("Manufactured", it.toString()))
        }

        // Expiration Date
        main.expirationDate?.let {
            props.add(TagDisplayItem.PropertyRow("Expires", it.toString()))
        }

        return props
    }
}
