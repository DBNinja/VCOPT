package org.openprinttag

import android.nfc.Tag
import android.nfc.tech.NfcA
import android.nfc.tech.NfcV
import java.io.IOException
import kotlin.math.min

class NfcHelper(private val tag: Tag) {

    enum class Tech { NfcA, NfcV, Unknown }

    fun detectTech(): Tech {
        return when {
            tag.techList.contains("android.nfc.tech.NfcA") -> Tech.NfcA
            tag.techList.contains("android.nfc.tech.NfcV") -> Tech.NfcV
            else -> Tech.Unknown
        }
    }

    fun readFullTag(): ByteArray {
        return when (detectTech()) {
            Tech.NfcA -> readNfcAFull()
            Tech.NfcV -> readNfcVFull()
            else -> throw IOException("Unsupported tag tech")
        }
    }

    fun writeFullTag(data: ByteArray): Boolean {
        return when (detectTech()) {
            Tech.NfcA -> writeNfcAFull(data)
            Tech.NfcV -> writeNfcVFull(data)
            else -> throw IOException("Unsupported tag tech")
        }
    }

    private fun readNfcAFull(): ByteArray {
        val nfcA = NfcA.get(tag) ?: throw IOException("NfcA not available")
        nfcA.connect()
        try {
            val startPage = 4
            val endPage = 129
            val cmd = byteArrayOf(0x3A.toByte(), startPage.toByte(), endPage.toByte())
            val resp = nfcA.transceive(cmd)
            return resp
        } finally {
            try { nfcA.close() } catch (ignored: Exception) {}
        }
    }

    private fun writeNfcAFull(data: ByteArray): Boolean {
        val nfcA = NfcA.get(tag) ?: throw IOException("NfcA not available")
        nfcA.connect()
        try {
            val pageSize = 4
            var page = 4
            var offset = 0
            while (offset < data.size) {
                val chunkLen = min(pageSize, data.size - offset)
                val buf = ByteArray(pageSize) { 0x00 }
                System.arraycopy(data, offset, buf, 0, chunkLen)
                val cmd = ByteArray(2 + pageSize)
                cmd[0] = 0xA2.toByte()
                cmd[1] = page.toByte()
                System.arraycopy(buf, 0, cmd, 2, pageSize)
                val resp = nfcA.transceive(cmd)
                offset += chunkLen
                page += 1
            }
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } finally {
            try { nfcA.close() } catch (ignored: Exception) {}
        }
    }

    private fun readNfcVFull(): ByteArray {
        val nfcV = NfcV.get(tag) ?: throw IOException("NfcV not available")
        nfcV.connect()
        try {
            val uid = tag.id.reversedArray()
            val blocks = mutableListOf<Byte>()
            for (blockNum in 0 until 129) {
                try {
                    val flags: Byte = 0x02
                    val cmd = ByteArray(2 + uid.size + 1)
                    cmd[0] = flags
                    cmd[1] = 0x20.toByte()
                    System.arraycopy(uid, 0, cmd, 2, uid.size)
                    cmd[2 + uid.size] = blockNum.toByte()
                    val resp = nfcV.transceive(cmd)
                    if (resp.size < 2) break
                    val blockData = resp.copyOfRange(1, resp.size)
                    blocks.addAll(blockData.toList())
                } catch (e: Exception) {
                    break
                }
            }
            return blocks.toByteArray()
        } finally {
            try { nfcV.close() } catch (ignored: Exception) {}
        }
    }

    private fun writeNfcVFull(data: ByteArray): Boolean {
        val nfcV = NfcV.get(tag) ?: throw IOException("NfcV not available")
        nfcV.connect()
        try {
            val uid = tag.id.reversedArray()
            val blockSize = 4
            var blockNum = 0
            var offset = 0
            while (offset < data.size) {
                val toWrite = ByteArray(blockSize) { 0x00 }
                val len = min(blockSize, data.size - offset)
                System.arraycopy(data, offset, toWrite, 0, len)
                val flags: Byte = 0x22
                val cmdCode: Byte = 0x21
                val cmd = ByteArray(2 + uid.size + 1 + blockSize)
                cmd[0] = flags
                cmd[1] = cmdCode
                System.arraycopy(uid, 0, cmd, 2, uid.size)
                cmd[2 + uid.size] = blockNum.toByte()
                System.arraycopy(toWrite, 0, cmd, 3 + uid.size, blockSize)
                val resp = nfcV.transceive(cmd)
                offset += len
                blockNum += 1
            }
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } finally {
            try { nfcV.close() } catch (ignored: Exception) {}
        }
    }
}
