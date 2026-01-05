package org.openprinttag.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OpenPrintTagModelTest {

    @Test
    fun test_mainRegion_defaultValues() {
        val mainRegion = MainRegion()

        assertEquals("Default materialClass should be FFF", "FFF", mainRegion.materialClass)
        assertNull("Default materialType should be null", mainRegion.materialType)
        assertNull("Default brandName should be null", mainRegion.brandName)
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
        val auxRegion = AuxRegion()

        assertNull("Default consumedWeight should be null", auxRegion.consumedWeight)
        assertNull("Default workgroup should be null", auxRegion.workgroup)
        assertNull("Default generalPurposeRangeUser should be null", auxRegion.generalPurposeRangeUser)
        assertNull("Default lastStirTime should be null", auxRegion.lastStirTime)
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
        assertNotNull("Aux region should have default value", model.aux)
        assertNull("URL region should be null by default", model.urlRecord)
    }

    @Test
    fun test_mainRegion_setValues() {
        val mainRegion = MainRegion().apply {
            materialClass = "SLA"
            materialType = "Standard Resin"
            brandName = "TestBrand"
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
        assertEquals("TestBrand", mainRegion.brandName)
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
        val mainRegion = MainRegion().apply {
            nominalNettoFullWeight = 1000f
            actualNettoFullWeight = 980f
            emptyContainerWeight = 200f
        }

        assertEquals("Nominal weight should be set", 1000f, mainRegion.nominalNettoFullWeight!!, 0.01f)
        assertEquals("Actual weight should be set", 980f, mainRegion.actualNettoFullWeight!!, 0.01f)
        assertEquals("Empty container weight should be set", 200f, mainRegion.emptyContainerWeight!!, 0.01f)
    }

    @Test
    fun test_mainRegion_temperatureFields() {
        val mainRegion = MainRegion().apply {
            minPrintTemp = 190
            maxPrintTemp = 230
            preheatTemp = 210
            minBedTemp = 50
            maxBedTemp = 70
            minChamberTemp = 40
            maxChamberTemp = 60
            chamberTemperature = 50
        }

        assertEquals(190, mainRegion.minPrintTemp)
        assertEquals(230, mainRegion.maxPrintTemp)
        assertEquals(210, mainRegion.preheatTemp)
        assertEquals(50, mainRegion.minBedTemp)
        assertEquals(70, mainRegion.maxBedTemp)
        assertEquals(40, mainRegion.minChamberTemp)
        assertEquals(60, mainRegion.maxChamberTemp)
        assertEquals(50, mainRegion.chamberTemperature)
    }

    @Test
    fun test_mainRegion_secondaryColors() {
        val mainRegion = MainRegion().apply {
            primaryColor = "FF0000"
            secondaryColor0 = "00FF00"
            secondaryColor1 = "0000FF"
            secondaryColor2 = "FFFF00"
            secondaryColor3 = "FF00FF"
            secondaryColor4 = "00FFFF"
        }

        assertEquals("FF0000", mainRegion.primaryColor)
        assertEquals("00FF00", mainRegion.secondaryColor0)
        assertEquals("0000FF", mainRegion.secondaryColor1)
        assertEquals("FFFF00", mainRegion.secondaryColor2)
        assertEquals("FF00FF", mainRegion.secondaryColor3)
        assertEquals("00FFFF", mainRegion.secondaryColor4)
    }

    @Test
    fun test_mainRegion_physicalProperties() {
        val mainRegion = MainRegion().apply {
            filamentDiameter = 1.75f
            density = 1.24f
            shoreHardnessA = 95
            shoreHardnessD = 45
            minNozzleDiameter = 0.4f
        }

        assertEquals(1.75f, mainRegion.filamentDiameter!!, 0.01f)
        assertEquals(1.24f, mainRegion.density!!, 0.01f)
        assertEquals(95, mainRegion.shoreHardnessA)
        assertEquals(45, mainRegion.shoreHardnessD)
        assertEquals(0.4f, mainRegion.minNozzleDiameter!!, 0.01f)
    }

    @Test
    fun test_mainRegion_containerDimensions() {
        val mainRegion = MainRegion().apply {
            containerWidth = 53
            containerOuterDiameter = 200
            containerInnerDiameter = 55
            containerHoleDiameter = 52
        }

        assertEquals(53, mainRegion.containerWidth)
        assertEquals(200, mainRegion.containerOuterDiameter)
        assertEquals(55, mainRegion.containerInnerDiameter)
        assertEquals(52, mainRegion.containerHoleDiameter)
    }

    @Test
    fun test_mainRegion_slaFields() {
        val mainRegion = MainRegion().apply {
            materialClass = "SLA"
            viscosity18c = 100f
            viscosity25c = 80f
            viscosity40c = 50f
            viscosity60c = 30f
            containerVolumetricCapacity = 1000f
            cureWavelength = 405
        }

        assertEquals("SLA", mainRegion.materialClass)
        assertEquals(100f, mainRegion.viscosity18c!!, 0.01f)
        assertEquals(80f, mainRegion.viscosity25c!!, 0.01f)
        assertEquals(50f, mainRegion.viscosity40c!!, 0.01f)
        assertEquals(30f, mainRegion.viscosity60c!!, 0.01f)
        assertEquals(1000f, mainRegion.containerVolumetricCapacity!!, 0.01f)
        assertEquals(405, mainRegion.cureWavelength)
    }

    @Test
    fun test_mainRegion_filamentLength() {
        val mainRegion = MainRegion().apply {
            nominalFullLength = 330000f  // mm (330m)
            actualFullLength = 332000f   // mm
        }

        assertEquals(330000f, mainRegion.nominalFullLength!!, 0.01f)
        assertEquals(332000f, mainRegion.actualFullLength!!, 0.01f)
    }

    @Test
    fun test_mainRegion_uuids() {
        val mainRegion = MainRegion().apply {
            instanceUuid = "00112233445566778899AABBCCDDEEFF"
            packageUuid = "11223344556677889900AABBCCDDEEFF"
            materialUuid = "22334455667788990011AABBCCDDEEFF"
            brandUuid = "33445566778899001122AABBCCDDEEFF"
        }

        assertEquals("00112233445566778899AABBCCDDEEFF", mainRegion.instanceUuid)
        assertEquals("11223344556677889900AABBCCDDEEFF", mainRegion.packageUuid)
        assertEquals("22334455667788990011AABBCCDDEEFF", mainRegion.materialUuid)
        assertEquals("33445566778899001122AABBCCDDEEFF", mainRegion.brandUuid)
    }

    @Test
    fun test_auxRegion_setValues() {
        val auxRegion = AuxRegion().apply {
            consumedWeight = 250f
            workgroup = "TestWorkgroup"
            generalPurposeRangeUser = "Custom data"
            lastStirTime = LocalDate.of(2024, 1, 15)
        }

        assertEquals(250f, auxRegion.consumedWeight!!, 0.01f)
        assertEquals("TestWorkgroup", auxRegion.workgroup)
        assertEquals("Custom data", auxRegion.generalPurposeRangeUser)
        assertEquals(LocalDate.of(2024, 1, 15), auxRegion.lastStirTime)
    }

    @Test
    fun test_model_copyValues() {
        val original = OpenPrintTagModel().apply {
            main.brandName = "OriginalBrand"
            main.materialType = "PLA"
        }

        val copy = original.copy()

        assertEquals("Copied brandName should match", original.main.brandName, copy.main.brandName)
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
        val mainRegion = MainRegion()
        mainRegion.materialTags = listOf("tag1", "tag2")

        assertEquals(2, mainRegion.materialTags.size)
        assertTrue(mainRegion.materialTags.contains("tag1"))
        assertTrue(mainRegion.materialTags.contains("tag2"))
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
            manufacturedDate = LocalDate.of(2024, 6, 15)
            expirationDate = LocalDate.of(2026, 6, 15)
        }

        assertEquals(LocalDate.of(2024, 6, 15), mainRegion.manufacturedDate)
        assertEquals(LocalDate.of(2026, 6, 15), mainRegion.expirationDate)
    }
}
