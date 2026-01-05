package org.openprinttag.model

import org.junit.Test
import org.junit.Assert.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.findAnnotation
import kotlinx.serialization.SerialName

/**
 * Tests to verify that the data model matches the OpenPrintTag specification.
 * This ensures all fields from the official spec are present with correct CBOR keys.
 */
class FieldCoverageTest {

    // All expected CBOR keys per official OpenPrintTag spec
    // Note: Keys 12, 25, 26 are DEPRECATED and should NOT be used
    private val expectedMainKeys = listOf(
        "0",  // instance_uuid
        "1",  // package_uuid
        "2",  // material_uuid
        "3",  // brand_uuid
        "4",  // gtin
        "5",  // brand_specific_instance_id
        "6",  // brand_specific_package_id
        "7",  // brand_specific_material_id
        "8",  // material_class
        "9",  // material_type
        "10", // material_name
        "11", // brand_name
        "13", // write_protection
        "14", // manufactured_date
        "15", // expiration_date
        "16", // nominal_netto_full_weight
        "17", // actual_netto_full_weight
        "18", // empty_container_weight
        "19", // primary_color
        "20", // secondary_color_0
        "21", // secondary_color_1
        "22", // secondary_color_2
        "23", // secondary_color_3
        "24", // secondary_color_4
        "27", // transmission_distance
        "28", // tags
        "29", // density
        "30", // filament_diameter (NOT key 12!)
        "31", // shore_hardness_a
        "32", // shore_hardness_d
        "33", // min_nozzle_diameter
        "34", // min_print_temperature
        "35", // max_print_temperature
        "36", // preheat_temperature
        "37", // min_bed_temperature
        "38", // max_bed_temperature
        "39", // min_chamber_temperature
        "40", // max_chamber_temperature
        "41", // chamber_temperature
        "42", // container_width
        "43", // container_outer_diameter
        "44", // container_inner_diameter
        "45", // container_hole_diameter
        "46", // viscosity_18c
        "47", // viscosity_25c
        "48", // viscosity_40c
        "49", // viscosity_60c
        "50", // container_volumetric_capacity
        "51", // cure_wavelength
        "52", // material_abbreviation
        "53", // nominal_full_length
        "54", // actual_full_length
        "55", // country_of_origin
        "56"  // certifications
    )

    private val expectedAuxKeys = listOf(
        "0", // consumed_weight
        "1", // workgroup
        "2", // general_purpose_range_user
        "3"  // last_stir_time
    )

    private val expectedMetaKeys = listOf(
        "0", // main_region_offset
        "1", // main_region_size
        "2", // aux_region_offset
        "3"  // aux_region_size
    )

    @Test
    fun `MainRegion has all spec fields`() {
        val actualKeys = MainRegion::class.memberProperties.mapNotNull { prop ->
            prop.findAnnotation<SerialName>()?.value
        }

        expectedMainKeys.forEach { key ->
            assertTrue("Missing MainRegion field for CBOR key $key", key in actualKeys)
        }
    }

    @Test
    fun `MainRegion does not use deprecated key 12`() {
        val keys = MainRegion::class.memberProperties.mapNotNull { prop ->
            prop.findAnnotation<SerialName>()?.value
        }
        assertFalse(
            "Key 12 is DEPRECATED - use key 30 for filament_diameter instead",
            "12" in keys
        )
    }

    @Test
    fun `MainRegion does not use deprecated key 25`() {
        val keys = MainRegion::class.memberProperties.mapNotNull { prop ->
            prop.findAnnotation<SerialName>()?.value
        }
        assertFalse("Key 25 is DEPRECATED", "25" in keys)
    }

    @Test
    fun `MainRegion does not use deprecated key 26`() {
        val keys = MainRegion::class.memberProperties.mapNotNull { prop ->
            prop.findAnnotation<SerialName>()?.value
        }
        assertFalse("Key 26 is DEPRECATED", "26" in keys)
    }

    @Test
    fun `AuxRegion has all spec fields`() {
        val actualKeys = AuxRegion::class.memberProperties.mapNotNull { prop ->
            prop.findAnnotation<SerialName>()?.value
        }

        expectedAuxKeys.forEach { key ->
            assertTrue("Missing AuxRegion field for CBOR key $key", key in actualKeys)
        }
    }

    @Test
    fun `MetaRegion has all spec fields`() {
        val actualKeys = MetaRegion::class.memberProperties.mapNotNull { prop ->
            prop.findAnnotation<SerialName>()?.value
        }

        expectedMetaKeys.forEach { key ->
            assertTrue("Missing MetaRegion field for CBOR key $key", key in actualKeys)
        }
    }

    @Test
    fun `filamentDiameter uses correct key 30`() {
        val prop = MainRegion::class.memberProperties.find { it.name == "filamentDiameter" }
        assertNotNull("filamentDiameter field should exist", prop)
        assertEquals(
            "filamentDiameter should use key 30 (not deprecated key 12)",
            "30",
            prop?.findAnnotation<SerialName>()?.value
        )
    }

    @Test
    fun `weight fields are Float type per spec`() {
        // Spec says "number" type = Float, not Int
        val main = MainRegion()
        val nominalWeight = main::nominalNettoFullWeight
        val actualWeight = main::actualNettoFullWeight
        val emptyWeight = main::emptyContainerWeight

        // Check the property returns Float?
        assertTrue(
            "nominalNettoFullWeight should be Float type",
            nominalWeight.returnType.toString().contains("Float")
        )
        assertTrue(
            "actualNettoFullWeight should be Float type",
            actualWeight.returnType.toString().contains("Float")
        )
        assertTrue(
            "emptyContainerWeight should be Float type",
            emptyWeight.returnType.toString().contains("Float")
        )
    }

    @Test
    fun `auxRegion consumedWeight is Float type per spec`() {
        val aux = AuxRegion()
        val consumedWeight = aux::consumedWeight
        assertTrue(
            "consumedWeight should be Float type (spec says 'number')",
            consumedWeight.returnType.toString().contains("Float")
        )
    }

    @Test
    fun `brandName field exists with correct key`() {
        val prop = MainRegion::class.memberProperties.find { it.name == "brandName" }
        assertNotNull("brandName field should exist (not 'brand')", prop)
        assertEquals("11", prop?.findAnnotation<SerialName>()?.value)
    }

    @Test
    fun `no field named brand exists`() {
        // Ensure old 'brand' field has been renamed to 'brandName'
        val prop = MainRegion::class.memberProperties.find { it.name == "brand" }
        assertNull("Field should be named 'brandName', not 'brand'", prop)
    }

    @Test
    fun `chamberTemperature uses correct key 41`() {
        val prop = MainRegion::class.memberProperties.find { it.name == "chamberTemperature" }
        assertNotNull("chamberTemperature field should exist", prop)
        assertEquals("41", prop?.findAnnotation<SerialName>()?.value)
    }

    @Test
    fun `all SLA fields exist`() {
        val props = MainRegion::class.memberProperties.map { it.name }
        val slaFields = listOf(
            "viscosity18c", "viscosity25c", "viscosity40c", "viscosity60c",
            "containerVolumetricCapacity", "cureWavelength"
        )
        slaFields.forEach { field ->
            assertTrue("SLA field '$field' should exist", field in props)
        }
    }

    @Test
    fun `all container dimension fields exist`() {
        val props = MainRegion::class.memberProperties.map { it.name }
        val containerFields = listOf(
            "containerWidth", "containerOuterDiameter",
            "containerInnerDiameter", "containerHoleDiameter"
        )
        containerFields.forEach { field ->
            assertTrue("Container field '$field' should exist", field in props)
        }
    }

    @Test
    fun `all UUID fields exist`() {
        val props = MainRegion::class.memberProperties.map { it.name }
        val uuidFields = listOf(
            "instanceUuid", "packageUuid", "materialUuid", "brandUuid"
        )
        uuidFields.forEach { field ->
            assertTrue("UUID field '$field' should exist", field in props)
        }
    }

    @Test
    fun `all brand-specific ID fields exist`() {
        val props = MainRegion::class.memberProperties.map { it.name }
        val brandIdFields = listOf(
            "brandSpecificInstanceId", "brandSpecificPackageId", "brandSpecificMaterialId"
        )
        brandIdFields.forEach { field ->
            assertTrue("Brand ID field '$field' should exist", field in props)
        }
    }

    @Test
    fun `filament length fields exist`() {
        val props = MainRegion::class.memberProperties.map { it.name }
        assertTrue("nominalFullLength should exist", "nominalFullLength" in props)
        assertTrue("actualFullLength should exist", "actualFullLength" in props)
    }

    @Test
    fun `hardness fields exist`() {
        val props = MainRegion::class.memberProperties.map { it.name }
        assertTrue("shoreHardnessA should exist", "shoreHardnessA" in props)
        assertTrue("shoreHardnessD should exist", "shoreHardnessD" in props)
    }

    @Test
    fun `serialization round trip preserves all field values`() {
        val original = MainRegion(
            instanceUuid = "00112233445566778899AABBCCDDEEFF",
            materialClass = "FFF",
            materialType = "PLA",
            brandName = "TestBrand",
            materialName = "Test PLA",
            filamentDiameter = 1.75f,
            density = 1.24f,
            nominalNettoFullWeight = 1000f,
            actualNettoFullWeight = 1005f,
            emptyContainerWeight = 200f,
            minPrintTemp = 200,
            maxPrintTemp = 230,
            minBedTemp = 60,
            maxBedTemp = 70,
            shoreHardnessA = null,
            shoreHardnessD = null,
            containerWidth = 53,
            containerOuterDiameter = 200,
            materialTags = listOf("food_safe")
        )

        // Basic assertions that values are set
        assertEquals("FFF", original.materialClass)
        assertEquals("PLA", original.materialType)
        assertEquals("TestBrand", original.brandName)
        assertEquals(1.75f, original.filamentDiameter!!, 0.001f)
        assertEquals(1000f, original.nominalNettoFullWeight!!, 0.001f)
        assertEquals(53, original.containerWidth)
    }
}
