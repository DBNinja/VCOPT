package com.drunkenblindninja.vcoprinttag.ui

import com.drunkenblindninja.vcoprinttag.model.OpenPrintTagModel

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
        val materialClass = main.material_class

        // 1. Material Header (always shown)
        val secondaryColors = listOfNotNull(
            main.secondary_color_0,
            main.secondary_color_1,
            main.secondary_color_2,
            main.secondary_color_3,
            main.secondary_color_4
        )

        items.add(TagDisplayItem.MaterialHeader(
            brandName = main.brand_name,
            materialName = main.material_name,
            materialType = main.material_type,
            materialClass = materialClass,
            primaryColor = main.primary_color,
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
        if (aux != null && (aux.consumed_weight != null || main.nominal_netto_full_weight != null)) {
            items.add(TagDisplayItem.SectionHeader("Usage"))
            items.add(TagDisplayItem.UsageProgress(
                totalWeight = main.actual_netto_full_weight ?: main.nominal_netto_full_weight,
                consumedWeight = aux.consumed_weight,
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
        if (main.tags.isNotEmpty()) {
            items.add(TagDisplayItem.SectionHeader("Tags"))
            items.add(TagDisplayItem.ChipGroup(main.tags, isOutlined = false))
        }

        if (main.certifications.isNotEmpty()) {
            items.add(TagDisplayItem.SectionHeader("Certifications"))
            items.add(TagDisplayItem.ChipGroup(main.certifications, isOutlined = true))
        }

        // 8. Metadata
        val metadataProps = buildMetadataProperties(main, model.urlRecord?.url)
        if (metadataProps.isNotEmpty()) {
            items.add(TagDisplayItem.SectionHeader("Details"))
            items.addAll(metadataProps)
        }

        return items
    }

    private fun buildPhysicalProperties(main: com.drunkenblindninja.vcoprinttag.model.MainRegion, materialClass: String): List<TagDisplayItem> {
        val props = mutableListOf<TagDisplayItem>()

        // Filament Diameter (FFF)
        main.filament_diameter?.let {
            props.add(TagDisplayItem.PropertyRow("Diameter", "${it}mm"))
        }

        // Weight with actual comparison
        if (main.nominal_netto_full_weight != null || main.actual_netto_full_weight != null) {
            val nominal = main.nominal_netto_full_weight?.let { "${it.toInt()}g" } ?: "N/A"
            val actual = main.actual_netto_full_weight?.let { "(actual: ${it.toInt()}g)" }
            props.add(TagDisplayItem.PropertyRow("Weight", nominal, actual))
        }

        // Density
        main.density?.let {
            props.add(TagDisplayItem.PropertyRow("Density", "${it} g/cm³"))
        }

        // Length with actual comparison
        if (main.nominal_full_length != null || main.actual_full_length != null) {
            val nominal = main.nominal_full_length?.let { "${it}m" } ?: "N/A"
            val actual = main.actual_full_length?.let { "(actual: ${it}m)" }
            props.add(TagDisplayItem.PropertyRow("Length", nominal, actual))
        }

        // Shore Hardness (for TPU and other flexible materials)
        if (main.shore_hardness_a != null || main.shore_hardness_d != null) {
            val hardness = buildString {
                main.shore_hardness_a?.let { append("${it}A") }
                if (main.shore_hardness_a != null && main.shore_hardness_d != null) append(" / ")
                main.shore_hardness_d?.let { append("${it}D") }
            }
            props.add(TagDisplayItem.PropertyRow("Shore Hardness", hardness))
        }

        // Min Nozzle Diameter
        main.min_nozzle_diameter?.let {
            props.add(TagDisplayItem.PropertyRow("Min Nozzle", "${it}mm"))
        }

        return props
    }

    private fun buildTemperatureProperties(main: com.drunkenblindninja.vcoprinttag.model.MainRegion): List<TagDisplayItem> {
        val props = mutableListOf<TagDisplayItem>()

        // Print Temperature Range
        if (main.min_print_temperature != null || main.max_print_temperature != null) {
            props.add(TagDisplayItem.TemperatureRow("Print", main.min_print_temperature, main.max_print_temperature))
        }

        // Bed Temperature Range
        if (main.min_bed_temperature != null || main.max_bed_temperature != null) {
            props.add(TagDisplayItem.TemperatureRow("Bed", main.min_bed_temperature, main.max_bed_temperature))
        }

        // Chamber Temperature
        if (main.min_chamber_temperature != null || main.max_chamber_temperature != null) {
            props.add(TagDisplayItem.TemperatureRow("Chamber", main.min_chamber_temperature, main.max_chamber_temperature))
        } else if (main.chamber_temperature != null) {
            props.add(TagDisplayItem.TemperatureRow("Chamber", null, null, main.chamber_temperature))
        }

        // Preheat Temperature
        main.preheat_temperature?.let {
            props.add(TagDisplayItem.TemperatureRow("Preheat", null, null, it))
        }

        return props
    }

    private fun buildSlaProperties(main: com.drunkenblindninja.vcoprinttag.model.MainRegion): List<TagDisplayItem> {
        val props = mutableListOf<TagDisplayItem>()

        // Viscosity at different temperatures
        main.viscosity_25c?.let {
            props.add(TagDisplayItem.PropertyRow("Viscosity @ 25°C", "${it} cP"))
        }

        // Cure Wavelength
        main.cure_wavelength?.let {
            props.add(TagDisplayItem.PropertyRow("Cure Wavelength", "${it}nm"))
        }

        // Container Volume
        main.container_volumetric_capacity?.let {
            props.add(TagDisplayItem.PropertyRow("Container Volume", "${it}ml"))
        }

        return props
    }

    private fun buildSpoolProperties(main: com.drunkenblindninja.vcoprinttag.model.MainRegion): List<TagDisplayItem> {
        val props = mutableListOf<TagDisplayItem>()

        // Spool dimensions
        main.container_outer_diameter?.let {
            props.add(TagDisplayItem.PropertyRow("Outer Diameter", "${it}mm"))
        }

        main.container_width?.let {
            props.add(TagDisplayItem.PropertyRow("Width", "${it}mm"))
        }

        main.container_hole_diameter?.let {
            props.add(TagDisplayItem.PropertyRow("Hole Diameter", "${it}mm"))
        }

        return props
    }

    private fun buildMetadataProperties(main: com.drunkenblindninja.vcoprinttag.model.MainRegion, url: String?): List<TagDisplayItem> {
        val props = mutableListOf<TagDisplayItem>()

        // GTIN
        main.gtin?.let {
            props.add(TagDisplayItem.PropertyRow("GTIN", it))
        }

        // Country of Origin
        main.country_of_origin?.let {
            props.add(TagDisplayItem.PropertyRow("Country", it))
        }

        // Manufactured Date
        main.manufactured_date?.let {
            props.add(TagDisplayItem.PropertyRow("Manufactured", it.toString()))
        }

        // Expiration Date
        main.expiration_date?.let {
            props.add(TagDisplayItem.PropertyRow("Expires", it.toString()))
        }

        // URL
        url?.takeIf { it.isNotBlank() }?.let {
            props.add(TagDisplayItem.PropertyRow("URL", it))
        }

        return props
    }
}
