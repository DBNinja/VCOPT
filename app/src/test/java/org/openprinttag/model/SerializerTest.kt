package org.openprinttag.model

import org.junit.Test
import org.junit.Assert.*

class SerializerTest {
    @Test
    fun testSerializeBasic() {
        val model = OpenPrintTagModel()
        model.main.materialClass = "FFF"
        model.main.materialType = "PLA"
        model.main.brand = "TestBrand"
        
        val classMap = mapOf("FFF" to 8)
        val typeMap = mapOf("PLA" to 0)
        val tagsMap = mapOf<String, Int>()
        val certsMap = mapOf<String, Int>()
        
        val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
        val bin = serializer.serialize(model)
        
        assertNotNull("Binary output should not be null", bin)
        assertTrue("Binary output should not be empty", bin.isNotEmpty())
        
        // E1 40 27 01 (Magic)
        assertEquals(0xE1.toByte(), bin[0])
    }
}
