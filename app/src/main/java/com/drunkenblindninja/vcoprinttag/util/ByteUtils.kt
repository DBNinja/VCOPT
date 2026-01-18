package com.drunkenblindninja.vcoprinttag.util

import java.nio.ByteBuffer
import java.util.zip.CRC32

object ByteUtils {
    fun floatToBytes(f: Float): ByteArray {
        return ByteBuffer.allocate(4).putFloat(f).array()
    }

    fun crc32(bytes: ByteArray): Int {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value.toInt()
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }
}
