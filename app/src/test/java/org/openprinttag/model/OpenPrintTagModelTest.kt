package org.openprinttag.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OpenPrintTagModelTest {

    @Test
    fun test_mainRegion_defaultValues() {
        val mainRegion = OpenPrintTagModel.MainRegion()

        assertEquals("Default materialClass should be FFF", "FFF", mainRegion.materialClass)
        assertNull("Default materialType should be null", mainRegion.materialType)
        assertNull("Default brand should be null", mainRegion.brand)
        assertNull("Default materialName should be null", mainRegion.materialName)
        assertNull("Default primaryColor should be null", mainRegion.primaryColor)
        assertNull("Default density should be null", mainRegion.density)
        assertNull("Default gtin should be null", mainRegion.gtin)
        assertNull("Default minPrintTemp should be null", mainRegion.minPrintTemp)
        assertNull("Default maxPrintTemp should be null", mainRegion.maxPrintTemp)
        assertTrue("Default materialTags should be empty", mainRegion.materialTags.isEmpty())
        assertTrue("Default certifications should be empty", mainRegion.certifications.isEmpty())
    }

    @Test
    fun test_auxRegion_defaultValues() {
        val auxRegion = OpenPrintTagModel.AuxRegion()

        assertNull("Default consumedWeight should be null", auxRegion.consumedWeight)
        assertEquals("Default workgroup should be empty string", "", auxRegion.workgroup)
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
        val urlRegion = OpenPrintTagModel.UrlRegion()

        assertEquals("Default URL should be openprinttag.org", "https://openprinttag.org", urlRegion.url)
        assertFalse("Default includeInTag should be false", urlRegion.includeInTag)
    }

    @Test
    fun test_openPrintTagModel_defaultStructure() {
        val model = OpenPrintTagModel()

        assertNotNull("Meta region should have default value", model.meta)
        assertNotNull("Main region should have default value", model.main)
        assertNotNull("Aux region should have default value", model.aux)
        assertNull("URL region should be null by default", model.urlRecord)
    }

    @Test
    fun test_mainRegion_setValues() {
        val mainRegion = OpenPrintTagModel.MainRegion().apply {
            materialClass = "SLA"
            materialType = "Standard Resin"
            brand = "TestBrand"
            materialName = "Test Resin"
            primaryColor = "FF0000"
            density = 1.1f
            gtin = "1234567890123"
            minPrintTemp = 25
            maxPrintTemp = 35
            materialTags = listOf("uv_sensitive", "food_safe")
        }

        assertEquals("SLA", mainRegion.materialClass)
        assertEquals("Standard Resin", mainRegion.materialType)
        assertEquals("TestBrand", mainRegion.brand)
        assertEquals("Test Resin", mainRegion.materialName)
        assertEquals("FF0000", mainRegion.primaryColor)
        assertEquals(1.1f, mainRegion.density!!, 0.001f)
        assertEquals("1234567890123", mainRegion.gtin)
        assertEquals(25, mainRegion.minPrintTemp)
        assertEquals(35, mainRegion.maxPrintTemp)
        assertEquals(2, mainRegion.materialTags.size)
    }

    @Test
    fun test_mainRegion_weightFields() {
        val mainRegion = OpenPrintTagModel.MainRegion().apply {
            totalWeight = 1000
            ActTotalWeight = 980
        }

        assertEquals("Total weight should be set", 1000, mainRegion.totalWeight)
        assertEquals("Actual total weight should be set", 980, mainRegion.ActTotalWeight)
    }

    @Test
    fun test_mainRegion_temperatureFields() {
        val mainRegion = OpenPrintTagModel.MainRegion().apply {
            minPrintTemp = 190
            maxPrintTemp = 230
            preheatTemp = 210
            minBedTemp = 50
            maxBedTemp = 70
            minChamberTemp = 40
            maxChamberTemp = 60
            idealChamberTemp = 50
        }

        assertEquals(190, mainRegion.minPrintTemp)
        assertEquals(230, mainRegion.maxPrintTemp)
        assertEquals(210, mainRegion.preheatTemp)
        assertEquals(50, mainRegion.minBedTemp)
        assertEquals(70, mainRegion.maxBedTemp)
        assertEquals(40, mainRegion.minChamberTemp)
        assertEquals(60, mainRegion.maxChamberTemp)
        assertEquals(50, mainRegion.idealChamberTemp)
    }

    @Test
    fun test_mainRegion_secondaryColors() {
        val mainRegion = OpenPrintTagModel.MainRegion().apply {
            primaryColor = "FF0000"
            secondary_color_0 = "00FF00"
            secondary_color_1 = "0000FF"
            secondary_color_2 = "FFFF00"
            secondary_color_3 = "FF00FF"
            secondary_color_4 = "00FFFF"
        }

        assertEquals("FF0000", mainRegion.primaryColor)
        assertEquals("00FF00", mainRegion.secondary_color_0)
        assertEquals("0000FF", mainRegion.secondary_color_1)
        assertEquals("FFFF00", mainRegion.secondary_color_2)
        assertEquals("FF00FF", mainRegion.secondary_color_3)
        assertEquals("00FFFF", mainRegion.secondary_color_4)
    }

    @Test
    fun test_auxRegion_setValues() {
        val auxRegion = OpenPrintTagModel.AuxRegion().apply {
            consumedWeight = 250
            workgroup = "TestWorkgroup"
        }

        assertEquals(250, auxRegion.consumedWeight)
        assertEquals("TestWorkgroup", auxRegion.workgroup)
    }

    @Test
    fun test_model_copyValues() {
        val original = OpenPrintTagModel().apply {
            main.brand = "OriginalBrand"
            main.materialType = "PLA"
        }

        val copy = original.copy()

        assertEquals("Copied brand should match", original.main.brand, copy.main.brand)
        assertEquals("Copied materialType should match", original.main.materialType, copy.main.materialType)
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
        val mainRegion = OpenPrintTagModel.MainRegion()
        mainRegion.materialTags = listOf("tag1", "tag2")

        assertEquals(2, mainRegion.materialTags.size)
        assertTrue(mainRegion.materialTags.contains("tag1"))
        assertTrue(mainRegion.materialTags.contains("tag2"))
    }

    @Test
    fun test_certifications_modifiable() {
        val mainRegion = OpenPrintTagModel.MainRegion()
        mainRegion.certifications = listOf("UL 2818", "UL 94 V0")

        assertEquals(2, mainRegion.certifications.size)
        assertTrue(mainRegion.certifications.contains("UL 2818"))
        assertTrue(mainRegion.certifications.contains("UL 94 V0"))
    }
}
