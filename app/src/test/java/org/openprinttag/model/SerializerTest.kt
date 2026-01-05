package org.openprinttag.model

import org.junit.Test
import org.junit.Assert.*

class SerializerTest {

    private val classMap = mapOf("FFF" to 0, "SLA" to 1, "SLS" to 2)
    private val typeMap = mapOf("PLA" to 0, "PETG" to 1, "ABS" to 2, "TPU" to 3)
    private val tagsMap = mapOf("food_safe" to 50, "uv_resistant" to 51, "esd_safe" to 52)
    private val certsMap = mapOf("FDA" to 0, "REACH" to 1)

    @Test
    fun testSerializeBasic() {
        val model = OpenPrintTagModel()
        model.main.materialClass = "FFF"
        model.main.materialType = "PLA"
        model.main.brand = "TestBrand"

        val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
        val bin = serializer.serialize(model)

        assertNotNull("Binary output should not be null", bin)
        assertTrue("Binary output should not be empty", bin.isNotEmpty())

        // E1 40 27 01 (NFC Magic)
        assertEquals(0xE1.toByte(), bin[0])
        assertEquals(0x40.toByte(), bin[1])
        assertEquals(0x27.toByte(), bin[2])
        assertEquals(0x01.toByte(), bin[3])
    }

    @Test
    fun testSerializeWithTemperatures() {
        val model = OpenPrintTagModel()
        model.main.materialClass = "FFF"
        model.main.materialType = "PLA"
        model.main.brand = "TestBrand"
        model.main.minPrintTemp = 190
        model.main.maxPrintTemp = 220

        val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
        val bin = serializer.serialize(model)

        assertNotNull("Binary output should not be null", bin)
        assertTrue("Should have reasonable size", bin.size > 20)
    }

    @Test
    fun testSerializeWithTags() {
        val model = OpenPrintTagModel()
        model.main.materialClass = "FFF"
        model.main.materialType = "PETG"
        model.main.brand = "Polymaker"
        model.main.materialName = "PolyLite PETG"
        model.main.materialTags = listOf("food_safe", "uv_resistant")
        model.main.density = 1.27f

        val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
        val bin = serializer.serialize(model)

        assertNotNull("Binary output should not be null", bin)
        assertTrue("Should have reasonable size for model with tags", bin.size > 30)
    }

    @Test
    fun testDeserializeHandlesInvalidData() {
        val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)

        // Empty data - should handle gracefully
        val emptyResult = serializer.deserialize(ByteArray(0))
        // Just ensure it doesn't crash

        // Random garbage - should handle gracefully
        val garbageResult = serializer.deserialize(byteArrayOf(0x00, 0x01, 0x02))
        // Just ensure it doesn't crash
    }

    @Test
    fun testDecodeUriRecord() {
        val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)

        // Test https://www. prefix (0x02)
        val payload1 = byteArrayOf(0x02) + "example.com".toByteArray()
        assertEquals("https://www.example.com", serializer.decodeUriRecord(payload1))

        // Test https:// prefix (0x04)
        val payload2 = byteArrayOf(0x04) + "example.com".toByteArray()
        assertEquals("https://example.com", serializer.decodeUriRecord(payload2))

        // Test http://www. prefix (0x01)
        val payload3 = byteArrayOf(0x01) + "example.com".toByteArray()
        assertEquals("http://www.example.com", serializer.decodeUriRecord(payload3))

        // Test empty payload
        assertEquals("", serializer.decodeUriRecord(ByteArray(0)))
    }

    @Test
    fun testGenerateDualRecordBin() {
        val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)

        // Create a simple CBOR payload
        val model = OpenPrintTagModel()
        model.main.brand = "Test"
        val cborPayload = byteArrayOf(0xA1.toByte(), 0x0B, 0x64, 0x54, 0x65, 0x73, 0x74) // {11: "Test"}

        val result = serializer.generateDualRecordBin(cborPayload, "https://www.openprinttag.org")

        assertNotNull("Dual record output should not be null", result)
        assertTrue("Should have NFC header", result[0] == 0xE1.toByte())
    }
}
