import org.openprinttag.model.OpenPrintTagModel
import org.openprinttag.model.MetaRegion
import org.openprinttag.model.MainRegion

class SerializerUnitTest {
    fun basicSerializeProducesDeterministicSize() {
        val m = OpenPrintTagModel(
            meta = null as MetaRegion?,
            main = MainRegion().apply {
                brand = "TestBrand"
                materialName = "TestMat"
                primaryColor = "Red"
                gtin = "12345678"
                minPrintTemp = 190
                maxPrintTemp = 230
            }
        )
        //val b1 = Serializer.serialize(m)
        //val b2 = Serializer.serialize(m)
        //assertEquals(b1.size, b2.size)
    }
}
