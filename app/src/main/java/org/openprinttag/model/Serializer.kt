package org.openprinttag.model

import org.openprinttag.util.ByteUtils
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import kotlin.math.min

object Serializer {

    private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

    fun serialize(model: OpenPrintTagModel): ByteArray {
        val meta = encodeMeta(model)
        val main = encodeMain(model)
        val aux  = encodeAux(model)

        val header = ByteBuffer.allocate(8)
        header.put("OPTG".toByteArray(StandardCharsets.US_ASCII))
        header.put(0x01)
        header.put(((meta.size shr 8) and 0xFF).toByte())
        header.put((meta.size and 0xFF).toByte())
        header.put(((main.size shr 8) and 0xFF).toByte())
        header.put((main.size and 0xFF).toByte())
        val headerBytes = header.array()

        val combined = ByteBuffer.allocate(headerBytes.size + meta.size + main.size + aux.size)
        combined.put(headerBytes)
        combined.put(meta)
        combined.put(main)
        combined.put(aux)
        val body = combined.array()

        val checksum = ByteUtils.crc32(body)
        val out = ByteBuffer.allocate(body.size + 4)
        out.put(body)
        out.putInt(checksum)
        return out.array()
    }

    private fun encodeMeta(m: OpenPrintTagModel): ByteArray {
        val bb = ByteBuffer.allocate(512)
        putTLV(bb, 0x01, m.brand.toByteArray(StandardCharsets.UTF_8))
        putTLV(bb, 0x02, m.materialName.toByteArray(StandardCharsets.UTF_8))
        putTLV(bb, 0x03, m.primaryColor.toByteArray(StandardCharsets.UTF_8))
        m.gtin?.let { putTLV(bb, 0x04, it.toByteArray(StandardCharsets.UTF_8)) }
        m.manufacturedDate?.let {
            putTLV(bb, 0x05, it.format(DATE_FMT).toByteArray(StandardCharsets.US_ASCII))
        }
        m.countryOfOrigin?.let { putTLV(bb, 0x06, it.toByteArray(StandardCharsets.US_ASCII)) }
        val size = bb.position()
        val out = ByteArray(size)
        bb.rewind(); bb.get(out)
        return out
    }

    private fun encodeMain(m: OpenPrintTagModel): ByteArray {
        val bb = ByteBuffer.allocate(512)
        putTLV(bb, 0x10, m.materialClass.toByteArray(StandardCharsets.US_ASCII))
        putTLV(bb, 0x11, m.materialType.toByteArray(StandardCharsets.US_ASCII))
        m.density?.let { putTLV(bb, 0x12, ByteUtils.floatToBytes(it)) }
        m.minPrintTemp?.let { putTLV(bb, 0x20, byteArrayOf(it.toByte())) }
        m.maxPrintTemp?.let { putTLV(bb, 0x21, byteArrayOf(it.toByte())) }
        m.preheatTemp?.let { putTLV(bb, 0x22, byteArrayOf(it.toByte())) }
        m.minBedTemp?.let { putTLV(bb, 0x23, byteArrayOf(it.toByte())) }
        m.maxBedTemp?.let { putTLV(bb, 0x24, byteArrayOf(it.toByte())) }
        if (m.materialTags.isNotEmpty()) {
            val joined = m.materialTags.joinToString(",")
            putTLV(bb, 0x30, joined.toByteArray(StandardCharsets.UTF_8))
        }
        if (m.certifications.isNotEmpty()) {
            val joined = m.certifications.joinToString(",")
            putTLV(bb, 0x31, joined.toByteArray(StandardCharsets.UTF_8))
        }
        val size = bb.position()
        val out = ByteArray(size)
        bb.rewind(); bb.get(out)
        return out
    }

    private fun encodeAux(m: OpenPrintTagModel): ByteArray {
        val bb = ByteBuffer.allocate(256)
        if (m.includeUrlRecord && !m.tagUrl.isNullOrBlank()) {
            putTLV(bb, 0x40, m.tagUrl!!.toByteArray(StandardCharsets.UTF_8))
        }
        val size = bb.position()
        val out = ByteArray(size)
        bb.rewind(); bb.get(out)
        return out
    }

    private fun putTLV(bb: java.nio.ByteBuffer, tag: Int, value: ByteArray) {
        val len = min(value.size, 255)
        bb.put(tag.toByte())
        bb.put(len.toByte())
        bb.put(value, 0, len)
    }
}
