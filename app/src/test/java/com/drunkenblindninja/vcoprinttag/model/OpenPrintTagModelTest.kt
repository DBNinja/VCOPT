package com.drunkenblindninja.vcoprinttag.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OpenPrintTagModelTest {

    @Test
    fun test_mainRegion_defaultValues() {
        val mainRegion = MainRegion()

        assertEquals("Default material_class should be FFF", "FFF", mainRegion.material_class)
        assertNull("Default material_type should be null", mainRegion.material_type)
        assertNull("Default brand_name should be null", mainRegion.brand_name)
        assertNull("Default material_name should be null", mainRegion.material_name)
        assertNull("Default primary_color should be null", mainRegion.primary_color)
        assertNull("Default density should be null", mainRegion.density)
        assertNull("Default gtin should be null", mainRegion.gtin)
        assertNull("Default min_print_temperature should be null", mainRegion.min_print_temperature)
        assertNull("Default max_print_temperature should be null", mainRegion.max_print_temperature)
        assertTrue("Default tags should be empty", mainRegion.tags.isEmpty())
        assertTrue("Default certifications should be empty", mainRegion.certifications.isEmpty())
    }

    @Test
    fun test_auxRegion_defaultValues() {
        val auxRegion = AuxRegion()

        assertNull("Default consumed_weight should be null", auxRegion.consumed_weight)
        assertNull("Default workgroup should be null", auxRegion.workgroup)
        assertNull("Default general_purpose_range_user should be null", auxRegion.general_purpose_range_user)
        assertNull("Default last_stir_time should be null", auxRegion.last_stir_time)
    }

    @Test
    fun test_metaRegion_defaultValues() {
        val metaRegion = MetaRegion()

        assertNull("Default mainRegionOffset should be null", metaRegion.mainRegionOffset)
        assertNull("Default mainRegionSize should be null", metaRegion.mainRegionSize)
        assertNull("Default auxRegionOffset should be null", metaRegion.auxRegionOffset)
        assertNull("Default auxRegionSize should be null", metaRegion.auxRegionSize)
    }

    @Test
    fun test_urlRegion_defaultValues() {
        val urlRegion = UrlRegion()

        assertEquals("Default URL should be openprinttag.org", "https://openprinttag.org", urlRegion.url)
        assertFalse("Default includeInTag should be false", urlRegion.includeInTag)
    }

    @Test
    fun test_openPrintTagModel_defaultStructure() {
        val model = OpenPrintTagModel()

        assertNotNull("Meta region should have default value", model.meta)
        assertNotNull("Main region should have default value", model.main)
        assertNull("Aux region should be null by default", model.aux)
        assertNull("URL region should be null by default", model.urlRecord)
    }

    @Test
    fun test_mainRegion_setValues() {
        val mainRegion = MainRegion().apply {
            material_class = "SLA"
            material_type = "Standard Resin"
            brand_name = "TestBrand"
            material_name = "Test Resin"
            primary_color = "FF0000"
            density = 1.1f
            gtin = "1234567890123"
            min_print_temperature = 25
            max_print_temperature = 35
            tags = listOf("uv_sensitive", "food_safe")
        }

        assertEquals("SLA", mainRegion.material_class)
        assertEquals("Standard Resin", mainRegion.material_type)
        assertEquals("TestBrand", mainRegion.brand_name)
        assertEquals("Test Resin", mainRegion.material_name)
        assertEquals("FF0000", mainRegion.primary_color)
        assertEquals(1.1f, mainRegion.density!!, 0.001f)
        assertEquals("1234567890123", mainRegion.gtin)
        assertEquals(25, mainRegion.min_print_temperature)
        assertEquals(35, mainRegion.max_print_temperature)
        assertEquals(2, mainRegion.tags.size)
    }

    @Test
    fun test_mainRegion_weightFields() {
        val mainRegion = MainRegion().apply {
            nominal_netto_full_weight = 1000f
            actual_netto_full_weight = 980f
            empty_container_weight = 200f
        }

        assertEquals("Nominal weight should be set", 1000f, mainRegion.nominal_netto_full_weight!!, 0.01f)
        assertEquals("Actual weight should be set", 980f, mainRegion.actual_netto_full_weight!!, 0.01f)
        assertEquals("Empty container weight should be set", 200f, mainRegion.empty_container_weight!!, 0.01f)
    }

    @Test
    fun test_mainRegion_temperatureFields() {
        val mainRegion = MainRegion().apply {
            min_print_temperature = 190
            max_print_temperature = 230
            preheat_temperature = 210
            min_bed_temperature = 50
            max_bed_temperature = 70
            min_chamber_temperature = 40
            max_chamber_temperature = 60
            chamber_temperature = 50
        }

        assertEquals(190, mainRegion.min_print_temperature)
        assertEquals(230, mainRegion.max_print_temperature)
        assertEquals(210, mainRegion.preheat_temperature)
        assertEquals(50, mainRegion.min_bed_temperature)
        assertEquals(70, mainRegion.max_bed_temperature)
        assertEquals(40, mainRegion.min_chamber_temperature)
        assertEquals(60, mainRegion.max_chamber_temperature)
        assertEquals(50, mainRegion.chamber_temperature)
    }

    @Test
    fun test_mainRegion_secondaryColors() {
        val mainRegion = MainRegion().apply {
            primary_color = "FF0000"
            secondary_color_0 = "00FF00"
            secondary_color_1 = "0000FF"
            secondary_color_2 = "FFFF00"
            secondary_color_3 = "FF00FF"
            secondary_color_4 = "00FFFF"
        }

        assertEquals("FF0000", mainRegion.primary_color)
        assertEquals("00FF00", mainRegion.secondary_color_0)
        assertEquals("0000FF", mainRegion.secondary_color_1)
        assertEquals("FFFF00", mainRegion.secondary_color_2)
        assertEquals("FF00FF", mainRegion.secondary_color_3)
        assertEquals("00FFFF", mainRegion.secondary_color_4)
    }

    @Test
    fun test_mainRegion_physicalProperties() {
        val mainRegion = MainRegion().apply {
            filament_diameter = 1.75f
            density = 1.24f
            shore_hardness_a = 95
            shore_hardness_d = 45
            min_nozzle_diameter = 0.4f
        }

        assertEquals(1.75f, mainRegion.filament_diameter!!, 0.01f)
        assertEquals(1.24f, mainRegion.density!!, 0.01f)
        assertEquals(95, mainRegion.shore_hardness_a)
        assertEquals(45, mainRegion.shore_hardness_d)
        assertEquals(0.4f, mainRegion.min_nozzle_diameter!!, 0.01f)
    }

    @Test
    fun test_mainRegion_containerDimensions() {
        val mainRegion = MainRegion().apply {
            container_width = 53
            container_outer_diameter = 200
            container_inner_diameter = 55
            container_hole_diameter = 52
        }

        assertEquals(53, mainRegion.container_width)
        assertEquals(200, mainRegion.container_outer_diameter)
        assertEquals(55, mainRegion.container_inner_diameter)
        assertEquals(52, mainRegion.container_hole_diameter)
    }

    @Test
    fun test_mainRegion_slaFields() {
        val mainRegion = MainRegion().apply {
            material_class = "SLA"
            viscosity_18c = 100f
            viscosity_25c = 80f
            viscosity_40c = 50f
            viscosity_60c = 30f
            container_volumetric_capacity = 1000f
            cure_wavelength = 405
        }

        assertEquals("SLA", mainRegion.material_class)
        assertEquals(100f, mainRegion.viscosity_18c!!, 0.01f)
        assertEquals(80f, mainRegion.viscosity_25c!!, 0.01f)
        assertEquals(50f, mainRegion.viscosity_40c!!, 0.01f)
        assertEquals(30f, mainRegion.viscosity_60c!!, 0.01f)
        assertEquals(1000f, mainRegion.container_volumetric_capacity!!, 0.01f)
        assertEquals(405, mainRegion.cure_wavelength)
    }

    @Test
    fun test_mainRegion_filamentLength() {
        val mainRegion = MainRegion().apply {
            nominal_full_length = 330f  // meters (stored as 330000mm in CBOR)
            actual_full_length = 332f   // meters (stored as 332000mm in CBOR)
        }

        assertEquals(330f, mainRegion.nominal_full_length!!, 0.01f)
        assertEquals(332f, mainRegion.actual_full_length!!, 0.01f)
    }

    @Test
    fun test_mainRegion_uuids() {
        val mainRegion = MainRegion().apply {
            instance_uuid = "00112233445566778899AABBCCDDEEFF"
            package_uuid = "11223344556677889900AABBCCDDEEFF"
            material_uuid = "22334455667788990011AABBCCDDEEFF"
            brand_uuid = "33445566778899001122AABBCCDDEEFF"
        }

        assertEquals("00112233445566778899AABBCCDDEEFF", mainRegion.instance_uuid)
        assertEquals("11223344556677889900AABBCCDDEEFF", mainRegion.package_uuid)
        assertEquals("22334455667788990011AABBCCDDEEFF", mainRegion.material_uuid)
        assertEquals("33445566778899001122AABBCCDDEEFF", mainRegion.brand_uuid)
    }

    @Test
    fun test_auxRegion_setValues() {
        val auxRegion = AuxRegion().apply {
            consumed_weight = 250f
            workgroup = "TestWorkgroup"
            general_purpose_range_user = "Custom data"
            last_stir_time = LocalDate.of(2024, 1, 15)
        }

        assertEquals(250f, auxRegion.consumed_weight!!, 0.01f)
        assertEquals("TestWorkgroup", auxRegion.workgroup)
        assertEquals("Custom data", auxRegion.general_purpose_range_user)
        assertEquals(LocalDate.of(2024, 1, 15), auxRegion.last_stir_time)
    }

    @Test
    fun test_model_copyValues() {
        val original = OpenPrintTagModel().apply {
            main.brand_name = "OriginalBrand"
            main.material_type = "PLA"
        }

        val copy = original.copy()

        assertEquals("Copied brand_name should match", original.main.brand_name, copy.main.brand_name)
        assertEquals("Copied material_type should match", original.main.material_type, copy.main.material_type)
    }

    @Test
    fun test_dateEpoch_conversion() {
        val date = LocalDate.of(2024, 1, 15)
        val epochSeconds = getDateEpoch(date)

        assertNotNull("Epoch conversion should produce non-null result", epochSeconds)
        assertTrue("Epoch should be a positive number", epochSeconds!! > 0)
    }

    @Test
    fun test_dateEpoch_nullDate() {
        val epochSeconds = getDateEpoch(null)

        assertNull("Null date should return null epoch", epochSeconds)
    }

    @Test
    fun test_materialTags_modifiable() {
        val mainRegion = MainRegion()
        mainRegion.tags = listOf("tag1", "tag2")

        assertEquals(2, mainRegion.tags.size)
        assertTrue(mainRegion.tags.contains("tag1"))
        assertTrue(mainRegion.tags.contains("tag2"))
    }

    @Test
    fun test_certifications_modifiable() {
        val mainRegion = MainRegion()
        mainRegion.certifications = listOf("UL 2818", "UL 94 V0")

        assertEquals(2, mainRegion.certifications.size)
        assertTrue(mainRegion.certifications.contains("UL 2818"))
        assertTrue(mainRegion.certifications.contains("UL 94 V0"))
    }

    @Test
    fun test_mainRegion_dates() {
        val mainRegion = MainRegion().apply {
            manufactured_date = LocalDate.of(2024, 6, 15)
            expiration_date = LocalDate.of(2026, 6, 15)
        }

        assertEquals(LocalDate.of(2024, 6, 15), mainRegion.manufactured_date)
        assertEquals(LocalDate.of(2026, 6, 15), mainRegion.expiration_date)
    }
}
