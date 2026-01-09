package org.openprinttag.model

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import java.time.LocalDate

/**
 * Comprehensive encode/decode tests based on the OpenPrintTag test suite.
 * Test data from: https://github.com/prusa3d/OpenPrintTag/tree/main/tests/encode_decode
 */
class OpenPrintTagEncodeDecodeTest {

    // Full enum maps matching the YAML specification
    private val classMap = mapOf("FFF" to 0, "SLA" to 1, "SLS" to 2)

    private val typeMap = mapOf(
        "PLA" to 0, "PETG" to 1, "TPU" to 2, "ABS" to 3, "ASA" to 4,
        "PC" to 5, "PCTG" to 6, "PP" to 7, "PA6" to 8, "PA11" to 9,
        "PA12" to 10, "PA66" to 11, "CPE" to 12, "TPE" to 13, "HIPS" to 14,
        "PHA" to 15, "PET" to 16, "PEI" to 17, "PBT" to 18, "PVB" to 19,
        "PVA" to 20, "PEKK" to 21, "PEEK" to 22, "BVOH" to 23, "TPC" to 24,
        "PPS" to 25, "PPSU" to 26, "PVC" to 27, "PEBA" to 28, "PVDF" to 29,
        "PPA" to 30, "PCL" to 31, "PES" to 32, "PMMA" to 33, "POM" to 34,
        "PPE" to 35, "PS" to 36, "PSU" to 37, "TPI" to 38, "SBS" to 39, "OBC" to 40
    )

    private val tagsMap = mapOf(
        "filtration_recommended" to 0, "biocompatible" to 1, "antibacterial" to 2,
        "air_filtering" to 3, "abrasive" to 4, "foaming" to 5, "self_extinguishing" to 6,
        "paramagnetic" to 7, "radiation_shielding" to 8, "high_temperature" to 9,
        "esd_safe" to 10, "conductive" to 11, "blend" to 12, "water_soluble" to 13,
        "ipa_soluble" to 14, "limonene_soluble" to 15, "matte" to 16, "silk" to 17,
        "translucent" to 19, "transparent" to 20, "iridescent" to 21, "pearlescent" to 22,
        "glitter" to 23, "glow_in_the_dark" to 24, "neon" to 25, "illuminescent_color_change" to 26,
        "temperature_color_change" to 27, "gradual_color_change" to 28, "coextruded" to 29,
        "contains_carbon" to 30, "contains_carbon_fiber" to 31, "contains_carbon_nano_tubes" to 32,
        "contains_glass" to 33, "contains_glass_fiber" to 34, "contains_kevlar" to 35,
        "contains_stone" to 36, "contains_magnetite" to 37, "contains_organic_material" to 38,
        "contains_cork" to 39, "contains_wax" to 40, "contains_wood" to 41, "contains_bamboo" to 42,
        "contains_pine" to 43, "contains_ceramic" to 44, "contains_boron_carbide" to 45,
        "contains_metal" to 46, "contains_bronze" to 47, "contains_iron" to 48, "contains_steel" to 49,
        "contains_silver" to 50, "contains_copper" to 51, "contains_aluminium" to 52, "contains_brass" to 53,
        "contains_tungsten" to 54, "imitates_wood" to 55, "imitates_metal" to 56, "imitates_marble" to 57,
        "imitates_stone" to 58, "lithophane" to 59, "recycled" to 60, "home_compostable" to 61,
        "industrially_compostable" to 62, "bio_based" to 63, "low_outgassing" to 64,
        "without_pigments" to 65, "contains_algae" to 66, "castable" to 67, "contains_ptfe" to 68,
        "limited_edition" to 69, "emi_shielding" to 70
    )

    private val certsMap = mapOf(
        "ul_2818" to 0, "ul_94_v0" to 1
    )

    private lateinit var serializer: Serializer

    @Before
    fun setup() {
        serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
    }

    private fun loadTestBin(name: String): ByteArray {
        return javaClass.classLoader!!.getResourceAsStream("encode_decode/${name}_data.bin")!!
            .readBytes()
    }

    // ==================== Test 01: PLA Prusa Galaxy Black ====================

    @Test
    fun test01_decode_plaGalaxyBlack() {
        val data = loadTestBin("01")
        val model = serializer.deserialize(data)

        assertNotNull("Model should not be null", model)
        model!!

        // Material info
        assertEquals("FFF", model.main.material_class)
        assertEquals("PLA", model.main.material_type)
        assertEquals("Prusament", model.main.brand_name)
        assertEquals("PLA Prusa Galaxy Black", model.main.material_name)

        // Package info
        assertEquals("8594173675001", model.main.gtin)
        assertEquals(1000f, model.main.nominal_netto_full_weight)

        // Instance info
        assertEquals("334c54f088", model.main.brand_specific_instance_id)
        assertEquals(1012f, model.main.actual_netto_full_weight)

        // Temperatures
        assertEquals(205, model.main.min_print_temperature)
        assertEquals(225, model.main.max_print_temperature)
        assertEquals(40, model.main.min_bed_temperature)
        assertEquals(60, model.main.max_bed_temperature)
        assertEquals(170, model.main.preheat_temperature)
        assertEquals(20, model.main.chamber_temperature)
        assertEquals(18, model.main.min_chamber_temperature)
        assertEquals(40, model.main.max_chamber_temperature)

        // Container info
        assertEquals(280f, model.main.empty_container_weight)
        assertEquals(200, model.main.container_outer_diameter)
        assertEquals(100, model.main.container_inner_diameter)
        assertEquals(52, model.main.container_hole_diameter)
        assertEquals(64, model.main.container_width)

        // Tags and certifications
        assertTrue("Should have glitter tag", model.main.tags.contains("glitter"))
        assertTrue("Should have ul_2818 cert", model.main.certifications.contains("ul_2818"))
        assertTrue("Should have ul_94_v0 cert", model.main.certifications.contains("ul_94_v0"))

        // URL record
        assertNotNull("Should have URL record", model.urlRecord)
        assertEquals("https://3dtag.org/s/334c54f088", model.urlRecord?.url)
    }

    @Test
    fun test01_roundTrip_plaGalaxyBlack() {
        val originalData = loadTestBin("01")
        val model = serializer.deserialize(originalData)
        assertNotNull(model)

        // Re-serialize (note: this won't include URL record)
        val reserialized = serializer.serialize(model!!)

        // Deserialize again
        val model2 = serializer.deserialize(reserialized)
        assertNotNull(model2)

        // Verify key fields match
        assertEquals(model.main.material_class, model2!!.main.material_class)
        assertEquals(model.main.material_type, model2.main.material_type)
        assertEquals(model.main.brand_name, model2.main.brand_name)
        assertEquals(model.main.material_name, model2.main.material_name)
        assertEquals(model.main.gtin, model2.main.gtin)
        assertEquals(model.main.min_print_temperature, model2.main.min_print_temperature)
        assertEquals(model.main.max_print_temperature, model2.main.max_print_temperature)
    }

    // ==================== Test 02: PETG Jet Black ====================

    @Test
    fun test02_decode_petgJetBlack() {
        val data = loadTestBin("02")
        val model = serializer.deserialize(data)

        assertNotNull("Model should not be null", model)
        model!!

        // Material info
        assertEquals("FFF", model.main.material_class)
        assertEquals("PETG", model.main.material_type)
        assertEquals("Prusament", model.main.brand_name)
        assertEquals("PETG Jet Black", model.main.material_name)
        assertEquals(1.27f, model.main.density!!, 0.01f)  // CBOR half-precision tolerance

        // Package info
        assertEquals("8594173675100", model.main.gtin)
        assertEquals(1000f, model.main.nominal_netto_full_weight)

        // Instance info
        assertEquals("7ab2acb509", model.main.brand_specific_instance_id)
        assertEquals(1050f, model.main.actual_netto_full_weight)

        // Temperatures
        assertEquals(240, model.main.min_print_temperature)
        assertEquals(260, model.main.max_print_temperature)
        assertEquals(70, model.main.min_bed_temperature)
        assertEquals(90, model.main.max_bed_temperature)
        assertEquals(170, model.main.preheat_temperature)
        assertEquals(35, model.main.chamber_temperature)
        assertEquals(18, model.main.min_chamber_temperature)
        assertEquals(60, model.main.max_chamber_temperature)

        // Container info
        assertEquals(280f, model.main.empty_container_weight)
        assertEquals(200, model.main.container_outer_diameter)
        assertEquals(100, model.main.container_inner_diameter)
        assertEquals(52, model.main.container_hole_diameter)
        assertEquals(64, model.main.container_width)

        // No tags for this material
        assertTrue("Should have no tags", model.main.tags.isEmpty())
    }

    @Test
    fun test02_roundTrip_petgJetBlack() {
        val originalData = loadTestBin("02")
        val model = serializer.deserialize(originalData)
        assertNotNull(model)

        val reserialized = serializer.serialize(model!!)
        val model2 = serializer.deserialize(reserialized)
        assertNotNull(model2)

        assertEquals(model.main.material_type, model2!!.main.material_type)
        assertEquals(model.main.density, model2.main.density)
        assertEquals(model.main.brand_name, model2.main.brand_name)
    }

    // ==================== Test 03: PETG with Aux Region ====================

    @Test
    fun test03_decode_petgWithAux() {
        val data = loadTestBin("03")
        val model = serializer.deserialize(data)

        assertNotNull("Model should not be null", model)
        model!!

        // Material info (note: no brand_name in test 03)
        assertEquals("FFF", model.main.material_class)
        assertEquals("PETG", model.main.material_type)
        assertEquals("PETG Jet Black", model.main.material_name)

        // Aux region - this test has aux data
        // Note: Test 03 is expected to have validation errors, but should still decode
        assertNotNull("Aux region may be present", model.aux)
    }

    // ==================== Test 04: PEKK Carbon Fiber ====================

    @Test
    fun test04_decode_pekkCarbonFiber() {
        val data = loadTestBin("04")
        val model = serializer.deserialize(data)

        assertNotNull("Model should not be null", model)
        model!!

        // Material info
        assertEquals("FFF", model.main.material_class)
        assertEquals("PEKK", model.main.material_type)
        assertEquals("3DXTech", model.main.brand_name)
        assertEquals("CarbonX PEKK-A+CF15 Black", model.main.material_name)
        assertEquals("PEKK-CF", model.main.material_abbreviation)
        assertEquals(1.39f, model.main.density!!, 0.01f)  // CBOR half-precision tolerance

        // Package info (no GTIN for this one)
        assertEquals(750f, model.main.nominal_netto_full_weight)

        // Instance info
        assertEquals(756f, model.main.actual_netto_full_weight)
        assertEquals(226000f, model.main.actual_full_length)

        // High-temp printing parameters
        assertEquals(360, model.main.min_print_temperature)
        assertEquals(390, model.main.max_print_temperature)
        assertEquals(120, model.main.min_bed_temperature)
        assertEquals(140, model.main.max_bed_temperature)
        assertEquals(90, model.main.chamber_temperature)
        assertEquals(60, model.main.min_chamber_temperature)
        assertEquals(140, model.main.max_chamber_temperature)

        // Min nozzle diameter
        assertEquals(0.4f, model.main.min_nozzle_diameter!!, 0.01f)  // CBOR half-precision tolerance

        // Tags - carbon fiber material
        assertTrue("Should have abrasive tag", model.main.tags.contains("abrasive"))
        assertTrue("Should have contains_carbon_fiber tag", model.main.tags.contains("contains_carbon_fiber"))
        assertTrue("Should have contains_carbon tag", model.main.tags.contains("contains_carbon"))
    }

    @Test
    fun test04_roundTrip_pekkCarbonFiber() {
        val originalData = loadTestBin("04")
        val model = serializer.deserialize(originalData)
        assertNotNull(model)

        val reserialized = serializer.serialize(model!!)
        val model2 = serializer.deserialize(reserialized)
        assertNotNull(model2)

        assertEquals(model.main.material_type, model2!!.main.material_type)
        assertEquals(model.main.material_abbreviation, model2.main.material_abbreviation)
        assertEquals(model.main.min_nozzle_diameter, model2.main.min_nozzle_diameter)
        assertEquals(model.main.actual_full_length, model2.main.actual_full_length)
    }

    // ==================== Test 05: Unknown Enum Values ====================

    @Test
    fun test05_decode_unknownEnumValues() {
        val data = loadTestBin("05")
        val model = serializer.deserialize(data)

        assertNotNull("Model should not be null even with unknown values", model)
        model!!

        // Material class should still be readable
        assertEquals("FFF", model.main.material_class)

        // Tags may include known values (glitter) and unknown values (9429)
        assertTrue("Should have glitter tag", model.main.tags.contains("glitter"))
    }

    // ==================== Encode Tests ====================

    @Test
    fun testEncode_basicPLA() {
        val model = OpenPrintTagModel()
        model.main.material_class = "FFF"
        model.main.material_type = "PLA"
        model.main.brand_name = "Prusament"
        model.main.material_name = "PLA Prusa Galaxy Black"
        model.main.gtin = "8594173675001"
        model.main.nominal_netto_full_weight = 1000f
        model.main.min_print_temperature = 205
        model.main.max_print_temperature = 225

        val bin = serializer.serialize(model)
        assertNotNull(bin)
        assertTrue("Should have reasonable size", bin.size > 50)

        // Verify NFC header
        assertEquals(0xE1.toByte(), bin[0])
        assertEquals(0x40.toByte(), bin[1])

        // Round-trip verification
        val decoded = serializer.deserialize(bin)
        assertNotNull(decoded)
        assertEquals("PLA", decoded!!.main.material_type)
        assertEquals("Prusament", decoded.main.brand_name)
        assertEquals(205, decoded.main.min_print_temperature)
    }

    @Test
    fun testEncode_withDualRecord() {
        val model = OpenPrintTagModel()
        model.main.material_class = "FFF"
        model.main.material_type = "PETG"
        model.main.brand_name = "TestBrand"

        // First serialize to get CBOR payload
        val singleRecord = serializer.serialize(model)

        // Extract just the CBOR payload (skip NDEF wrapper)
        // For dual record, we need the raw CBOR
        val cborPayload = extractCborPayload(singleRecord)

        // Generate dual record with URL
        val dualRecord = serializer.generateDualRecordBin(cborPayload, "https://www.example.com")

        assertNotNull(dualRecord)
        assertTrue("Dual record should be larger", dualRecord.size > singleRecord.size)

        // Should be able to deserialize and get URL
        val decoded = serializer.deserialize(dualRecord)
        assertNotNull(decoded)
        assertEquals("PETG", decoded!!.main.material_type)
        assertNotNull("Should have URL record", decoded.urlRecord)
        assertEquals("https://www.example.com", decoded.urlRecord?.url)
    }

    /**
     * Extract raw CBOR payload from serialized data (skipping NDEF headers)
     */
    private fun extractCborPayload(data: ByteArray): ByteArray {
        // Find NDEF message (0x03) and extract CBOR
        for (i in 4 until minOf(data.size, 20)) {
            if (data[i] == 0x03.toByte()) {
                val lengthByte = data[i + 1].toInt() and 0xFF
                val recordStart = if (lengthByte == 0xFF) i + 4 else i + 2

                // Parse record header
                val recordHeader = data[recordStart].toInt() and 0xFF
                val isShortRecord = (recordHeader and 0x10) != 0
                val typeLength = data[recordStart + 1].toInt() and 0xFF
                val payloadLengthOffset = recordStart + 2

                val payloadLength = if (isShortRecord) {
                    data[payloadLengthOffset].toInt() and 0xFF
                } else {
                    ((data[payloadLengthOffset].toInt() and 0xFF) shl 24) or
                    ((data[payloadLengthOffset + 1].toInt() and 0xFF) shl 16) or
                    ((data[payloadLengthOffset + 2].toInt() and 0xFF) shl 8) or
                    (data[payloadLengthOffset + 3].toInt() and 0xFF)
                }

                val payloadStart = if (isShortRecord) {
                    payloadLengthOffset + 1 + typeLength
                } else {
                    payloadLengthOffset + 4 + typeLength
                }

                return data.copyOfRange(payloadStart, payloadStart + payloadLength)
            }
        }
        return ByteArray(0)
    }
}
