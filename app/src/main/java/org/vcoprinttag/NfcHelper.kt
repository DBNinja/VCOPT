package org.vcoprinttag

import android.nfc.Tag
import android.nfc.tech.NfcA
import android.nfc.tech.NfcV
import android.util.Log
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
        val raw = when (detectTech()) {
            Tech.NfcA -> readNfcAFull()
            Tech.NfcV -> readNfcVFull()
            else -> throw IOException("Unsupported tag tech")
        }
        if (raw.isEmpty()) throw IOException("Failed to read any data from tag")
        return trimAtNdefTerminator(raw)
    }

    /**
     * Trim data at the NDEF terminator TLV (0xFE).
     * Everything after the terminator is padding/garbage.
     */
    private fun trimAtNdefTerminator(data: ByteArray): ByteArray {
        val terminatorIndex = data.indexOf(0xFE.toByte())
        return if (terminatorIndex >= 0) {
            data.copyOfRange(0, terminatorIndex + 1)
        } else {
            data
        }
    }

    fun writeFullTag(data: ByteArray): Boolean {
        return when (detectTech()) {
            Tech.NfcA -> writeNfcAFull(data)
            Tech.NfcV -> writeNfcVFull(data)
            else -> throw IOException("Unsupported tag tech")
        }
    }

    /**
     * Write data to specific byte offset on the tag.
     * Used for partial aux region updates.
     * @param byteOffset Byte offset from start of user data (after NFC header pages)
     * @param data Data to write (will be padded to page boundary)
     */
    fun writeAtOffset(byteOffset: Int, data: ByteArray): Boolean {
        return when (detectTech()) {
            Tech.NfcA -> writeNfcAAtOffset(byteOffset, data)
            Tech.NfcV -> writeNfcVAtOffset(byteOffset, data)
            else -> throw IOException("Unsupported tag tech")
        }
    }

    private fun writeNfcAAtOffset(byteOffset: Int, data: ByteArray): Boolean {
        val nfcA = NfcA.get(tag) ?: throw IOException("NfcA not available")
        nfcA.connect()
        try {
            val pageSize = 4
            // Page 4 is where user data starts (pages 0-3 are header/CC)
            val startPage = 4 + (byteOffset / pageSize)
            val pageOffset = byteOffset % pageSize

            var dataIndex = 0
            var currentPage = startPage

            // If starting mid-page, we need to read-modify-write the first page
            if (pageOffset > 0) {
                // Read current page content
                val readCmd = byteArrayOf(0x30.toByte(), currentPage.toByte())
                val currentData = nfcA.transceive(readCmd)

                // Merge with new data
                val merged = currentData.copyOf(pageSize)
                val bytesToCopy = min(pageSize - pageOffset, data.size)
                System.arraycopy(data, 0, merged, pageOffset, bytesToCopy)

                // Write merged page
                val writeCmd = ByteArray(2 + pageSize)
                writeCmd[0] = 0xA2.toByte()
                writeCmd[1] = currentPage.toByte()
                System.arraycopy(merged, 0, writeCmd, 2, pageSize)
                nfcA.transceive(writeCmd)

                dataIndex += bytesToCopy
                currentPage++
            }

            // Write remaining full pages
            while (dataIndex < data.size) {
                val remaining = data.size - dataIndex
                val buf = ByteArray(pageSize) { 0x00 }
                val chunkLen = min(pageSize, remaining)
                System.arraycopy(data, dataIndex, buf, 0, chunkLen)

                val cmd = ByteArray(2 + pageSize)
                cmd[0] = 0xA2.toByte()
                cmd[1] = currentPage.toByte()
                System.arraycopy(buf, 0, cmd, 2, pageSize)
                nfcA.transceive(cmd)

                dataIndex += chunkLen
                currentPage++
            }
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } finally {
            try { nfcA.close() } catch (ignored: Exception) {}
        }
    }

    private fun writeNfcVAtOffset(byteOffset: Int, data: ByteArray): Boolean {
        val nfcV = NfcV.get(tag) ?: throw IOException("NfcV not available")
        Log.d("NfcHelper", "Starting NFC-V write at offset $byteOffset, ${data.size} bytes")
        nfcV.connect()
        try {
            val uid = tag.id.reversedArray()
            val blockSize = 4
            val startBlock = byteOffset / blockSize
            val blockOffset = byteOffset % blockSize

            var dataIndex = 0
            var currentBlock = startBlock

            // If starting mid-block, read-modify-write
            if (blockOffset > 0) {
                // Read current block
                val readCmd = ByteArray(2 + uid.size + 1)
                readCmd[0] = 0x20  // Address flag only (standard data rate for compatibility)
                readCmd[1] = 0x20.toByte()  // READ_SINGLE_BLOCK command
                System.arraycopy(uid, 0, readCmd, 2, uid.size)
                readCmd[2 + uid.size] = currentBlock.toByte()
                val resp = nfcV.transceive(readCmd)
                val currentData = resp.copyOfRange(1, resp.size)

                // Merge with new data
                val merged = currentData.copyOf(blockSize)
                val bytesToCopy = min(blockSize - blockOffset, data.size)
                System.arraycopy(data, 0, merged, blockOffset, bytesToCopy)

                // Write merged block
                val writeCmd = ByteArray(2 + uid.size + 1 + blockSize)
                writeCmd[0] = 0x20  // Address flag only (standard data rate for compatibility)
                writeCmd[1] = 0x21.toByte()
                System.arraycopy(uid, 0, writeCmd, 2, uid.size)
                writeCmd[2 + uid.size] = currentBlock.toByte()
                System.arraycopy(merged, 0, writeCmd, 3 + uid.size, blockSize)
                val writeResp = nfcV.transceive(writeCmd)
                // Check response for errors
                if (writeResp.isEmpty() || (writeResp[0].toInt() and 0x01) != 0) {
                    val errorCode = if (writeResp.size > 1) writeResp[1].toInt() and 0xFF else -1
                    Log.e("NfcHelper", "Write failed at block $currentBlock (merge), error code: $errorCode")
                    return false
                }

                dataIndex += bytesToCopy
                currentBlock++
            }

            // Write remaining full blocks
            while (dataIndex < data.size) {
                val toWrite = ByteArray(blockSize) { 0x00 }
                val len = min(blockSize, data.size - dataIndex)
                System.arraycopy(data, dataIndex, toWrite, 0, len)

                val cmd = ByteArray(2 + uid.size + 1 + blockSize)
                cmd[0] = 0x20  // Address flag only (standard data rate for compatibility)
                cmd[1] = 0x21.toByte()
                System.arraycopy(uid, 0, cmd, 2, uid.size)
                cmd[2 + uid.size] = currentBlock.toByte()
                System.arraycopy(toWrite, 0, cmd, 3 + uid.size, blockSize)
                val resp = nfcV.transceive(cmd)
                // Check response for errors
                if (resp.isEmpty() || (resp[0].toInt() and 0x01) != 0) {
                    val errorCode = if (resp.size > 1) resp[1].toInt() and 0xFF else -1
                    Log.e("NfcHelper", "Write failed at block $currentBlock, error code: $errorCode")
                    return false
                }

                dataIndex += len
                currentBlock++
            }
            Log.d("NfcHelper", "Write at offset complete, ${currentBlock - startBlock} blocks written")
            return true
        } catch (e: IOException) {
            Log.e("NfcHelper", "Write at offset exception: ${e.message}")
            e.printStackTrace()
            return false
        } finally {
            try { nfcV.close() } catch (ignored: Exception) {}
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
        Log.d("NfcHelper", "Starting NFC-V read, UID: ${tag.id.joinToString("") { "%02X".format(it) }}")
        nfcV.connect()
        try {
            val uid = tag.id.reversedArray()
            val blocks = mutableListOf<Byte>()
            for (blockNum in 0 until 129) {
                try {
                    val flags: Byte = 0x20  // Address flag only (standard data rate for compatibility)
                    val cmd = ByteArray(2 + uid.size + 1)
                    cmd[0] = flags
                    cmd[1] = 0x20.toByte()  // READ_SINGLE_BLOCK command
                    System.arraycopy(uid, 0, cmd, 2, uid.size)
                    cmd[2 + uid.size] = blockNum.toByte()
                    val resp = nfcV.transceive(cmd)
                    if (resp.size < 2) {
                        Log.w("NfcHelper", "Short response at block $blockNum: ${resp.size} bytes")
                        break
                    }
                    val blockData = resp.copyOfRange(1, resp.size)
                    blocks.addAll(blockData.toList())
                } catch (e: Exception) {
                    Log.w("NfcHelper", "Failed at block $blockNum: ${e.message}")
                    break
                }
            }
            Log.d("NfcHelper", "Read ${blocks.size} bytes total from NFC-V tag")
            return blocks.toByteArray()
        } finally {
            try { nfcV.close() } catch (ignored: Exception) {}
        }
    }

    private fun writeNfcVFull(data: ByteArray): Boolean {
        val nfcV = NfcV.get(tag) ?: throw IOException("NfcV not available")
        Log.d("NfcHelper", "Starting NFC-V write, ${data.size} bytes, UID: ${tag.id.joinToString("") { "%02X".format(it) }}")
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
                val flags: Byte = 0x20  // Address flag only (standard data rate for compatibility)
                val cmdCode: Byte = 0x21
                val cmd = ByteArray(2 + uid.size + 1 + blockSize)
                cmd[0] = flags
                cmd[1] = cmdCode
                System.arraycopy(uid, 0, cmd, 2, uid.size)
                cmd[2 + uid.size] = blockNum.toByte()
                System.arraycopy(toWrite, 0, cmd, 3 + uid.size, blockSize)
                val resp = nfcV.transceive(cmd)
                // Check response for errors (bit 0 of flags byte indicates error)
                if (resp.isEmpty() || (resp[0].toInt() and 0x01) != 0) {
                    val errorCode = if (resp.size > 1) resp[1].toInt() and 0xFF else -1
                    Log.e("NfcHelper", "Write failed at block $blockNum, error code: $errorCode")
                    return false
                }
                offset += len
                blockNum += 1
            }
            Log.d("NfcHelper", "Write complete, $blockNum blocks written")
            return true
        } catch (e: IOException) {
            Log.e("NfcHelper", "Write exception: ${e.message}")
            e.printStackTrace()
            return false
        } finally {
            try { nfcV.close() } catch (ignored: Exception) {}
        }
    }
}
