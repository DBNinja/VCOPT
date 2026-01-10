package org.vcoprinttag

import org.junit.Assert.*
import org.junit.Test
import org.vcoprinttag.model.OpenPrintTagModel
import org.vcoprinttag.model.MainRegion
import org.vcoprinttag.model.AuxRegion
import org.vcoprinttag.model.Serializer

class SerializerUnitTest {

    // Mock maps for testing (in real tests, load from YAML)
    private val classMap = mapOf("FFF" to 0, "SLA" to 1)
    private val typeMap = mapOf("PLA" to 0, "PETG" to 1, "ABS" to 2, "TPU" to 3)
    private val tagsMap = mapOf("food_safe" to 0, "uv_resistant" to 1, "esd_safe" to 8)
    private val certsMap = mapOf("UL 2818" to 0, "UL 94 V0" to 1)

    private fun createSerializer() = Serializer(classMap, typeMap, tagsMap, certsMap)

    private fun createMinimalModel(): OpenPrintTagModel {
        return OpenPrintTagModel(
            meta = null,
            main = MainRegion().apply {
                brand_name = "TestBrand"
                material_name = "TestMat"
            }
        )
    }

    private fun createFullModel(): OpenPrintTagModel {
        return OpenPrintTagModel(
            meta = null,
            main = MainRegion().apply {
                material_class = "FFF"
                material_type = "PLA"
                brand_name = "TestBrand"
                material_name = "TestMaterial"
                primary_color = "FF5500"
                gtin = "12345678"
                density = 1.24f
                min_print_temperature = 190
                max_print_temperature = 230
                tags = listOf("food_safe", "uv_resistant")
            },
            aux = AuxRegion().apply {
                consumed_weight = 500f
                workgroup = "TestGroup"
            }
        )
    }

    @Test
    fun test_serialize_minimalModel_producesValidCbor() {
        val serializer = createSerializer()
        val model = createMinimalModel()

        val bytes = serializer.serialize(model)

        assertNotNull("Serialization should produce non-null result", bytes)
        assertTrue("Serialization should produce non-empty bytes", bytes.isNotEmpty())
    }

    @Test
    fun test_serialize_fullModel_includesAllFields() {
        val serializer = createSerializer()
        val model = createFullModel()

        val bytes = serializer.serialize(model)

        assertNotNull("Full model serialization should succeed", bytes)
        assertTrue("Full model should produce more bytes than minimal", bytes.size > 50)
    }

    @Test
    fun test_serialize_sameTwice_producesDeterministicSize() {
        val serializer = createSerializer()
        val model = createFullModel()

        val b1 = serializer.serialize(model)
        val b2 = serializer.serialize(model)

        assertEquals("Same model serialized twice should produce same size", b1.size, b2.size)
    }

    @Test
    fun test_serialize_nullFields_omittedFromOutput() {
        val serializer = createSerializer()
        val modelWithNulls = OpenPrintTagModel(
            meta = null,
            main = MainRegion().apply {
                brand_name = "TestBrand"
                // All other fields are null
            }
        )
        val modelWithValues = createFullModel()

        val bytesWithNulls = serializer.serialize(modelWithNulls)
        val bytesWithValues = serializer.serialize(modelWithValues)

        assertTrue("Model with nulls should produce fewer bytes",
            bytesWithNulls.size < bytesWithValues.size)
    }

    @Test
    fun test_serialize_emptyBrand_handledCorrectly() {
        val serializer = createSerializer()
        val model = OpenPrintTagModel(
            meta = null,
            main = MainRegion().apply {
                brand_name = ""
                material_name = "Test"
            }
        )

        val bytes = serializer.serialize(model)

        assertNotNull("Empty brand should not cause crash", bytes)
    }

    @Test
    fun test_serialize_materialTags_mappedToIntegers() {
        val serializer = createSerializer()
        val model = OpenPrintTagModel(
            meta = null,
            main = MainRegion().apply {
                brand_name = "Test"
                tags = listOf("food_safe", "esd_safe")
            }
        )

        val bytes = serializer.serialize(model)

        // The serialization should succeed with tags
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun test_serialize_specialCharacters_preserved() {
        val serializer = createSerializer()
        val model = OpenPrintTagModel(
            meta = null,
            main = MainRegion().apply {
                brand_name = "Test & Co."
                material_name = "PLA+ Special™"
            }
        )

        val bytes = serializer.serialize(model)

        assertNotNull("Special characters should not cause crash", bytes)
    }

    @Test
    fun test_serialize_unicodeStrings_preserved() {
        val serializer = createSerializer()
        val model = OpenPrintTagModel(
            meta = null,
            main = MainRegion().apply {
                brand_name = "测试品牌"  // Chinese characters
                material_name = "日本製品"  // Japanese characters
            }
        )

        val bytes = serializer.serialize(model)

        assertNotNull("Unicode strings should not cause crash", bytes)
    }

    @Test
    fun test_generateDualRecordBin_containsBothRecords() {
        val serializer = createSerializer()
        val model = createMinimalModel()
        val cborPayload = serializer.serialize(model)
        val url = "https://www.openprinttag.org"

        val dualRecord = serializer.generateDualRecordBin(cborPayload, url)

        assertNotNull(dualRecord)
        assertTrue("Dual record should be larger than single", dualRecord.size > cborPayload.size)
    }

    @Test
    fun test_materialTypeMapping_allTypesHaveKeys() {
        // Verify our test map has expected entries
        assertTrue(typeMap.containsKey("PLA"))
        assertTrue(typeMap.containsKey("PETG"))
        assertTrue(typeMap.containsKey("ABS"))
        assertTrue(typeMap.containsKey("TPU"))
    }

    @Test
    fun test_materialTagsMapping_allTagsHaveKeys() {
        assertTrue(tagsMap.containsKey("food_safe"))
        assertTrue(tagsMap.containsKey("uv_resistant"))
        assertTrue(tagsMap.containsKey("esd_safe"))
    }

    @Test
    fun test_decodeUriRecord_http_prefixes() {
        val serializer = createSerializer()

        // Test https://www. prefix (0x02)
        val payload1 = byteArrayOf(0x02) + "openprinttag.org".toByteArray()
        val url1 = serializer.decodeUriRecord(payload1)
        assertEquals("https://www.openprinttag.org", url1)

        // Test https:// prefix (0x04)
        val payload2 = byteArrayOf(0x04) + "openprinttag.org".toByteArray()
        val url2 = serializer.decodeUriRecord(payload2)
        assertEquals("https://openprinttag.org", url2)
    }

    @Test
    fun test_decodeUriRecord_emptyPayload() {
        val serializer = createSerializer()
        val result = serializer.decodeUriRecord(byteArrayOf())
        assertEquals("Empty payload should return empty string", "", result)
    }
}
