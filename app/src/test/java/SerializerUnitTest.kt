import org.junit.Assert.assertEquals
import org.junit.Test
import org.openprinttag.model.OpenPrintTagModel
import org.openprinttag.model.Serializer

class SerializerUnitTest {
    @Test
    fun basicSerializeProducesDeterministicSize() {
        val m = OpenPrintTagModel(
            brand = "TestBrand",
            materialName = "TestMat",
            primaryColor = "Red",
            gtin = "12345678",
            minPrintTemp = 190,
            maxPrintTemp = 230
        )
        val b1 = Serializer.serialize(m)
        val b2 = Serializer.serialize(m)
        assertEquals(b1.size, b2.size)
    }
}
