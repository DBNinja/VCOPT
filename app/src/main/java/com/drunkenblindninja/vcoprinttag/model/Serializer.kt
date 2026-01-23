@file:OptIn(ExperimentalStdlibApi::class)

package com.drunkenblindninja.vcoprinttag.model

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Sealed class representing parsed NDEF record types
 */
private sealed class NdefRecord {
    data class CborRecord(val payload: ByteArray) : NdefRecord()
    data class UriRecord(val url: String) : NdefRecord()
    data class Unknown(val type: String) : NdefRecord()
}

/**
 * Result of deserializing a tag, including aux region location info for partial writes
 */
data class DeserializeResult(
    val model: OpenPrintTagModel,
    val auxByteOffset: Int?,      // Byte offset of aux region from start of NFC data
    val auxByteSize: Int?,        // Size of aux region in bytes
    val cborPayloadOffset: Int?   // Byte offset where CBOR payload starts
)

class Serializer(
    private val classMap: Map<String, Int>,
    private val typeMap: Map<String, Int>,
    private val tagsMap: Map<String, Int>,
    private val certsMap: Map<String, Int>
) {

    private val mapper = ObjectMapper(CBORFactory())

    companion object {
        const val SPEC_SIZE_LIMIT = 316
    }

    fun serialize(model: OpenPrintTagModel, reserveAuxSpace: Boolean = false): ByteArray {
        // 1. Encode main and aux regions as CBOR maps
        val mainRegion = encodeMain(model)
        val auxRegion = encodeAux(model)

        // 2. Determine if we need a meta region (to specify aux offset)
        // We create a meta region if:
        // - We're reserving space for aux, OR
        // - We have actual aux data
        val needsMeta = reserveAuxSpace || auxRegion.isNotEmpty()

        // 3. Build the CBOR payload (meta + main + aux)
        val cborPayload: ByteArray
        if (needsMeta) {
            // First pass: calculate meta size by encoding a placeholder
            // Meta region format: {0: main_offset, 2: aux_offset}
            val placeholderMeta = encodeMeta(mainOffset = 10, auxOffset = 100) // rough estimate
            val estimatedMetaSize = placeholderMeta.size

            // Calculate actual offsets
            val mainOffset = estimatedMetaSize
            val auxOffset = mainOffset + mainRegion.size

            // Encode final meta region with correct offsets
            var metaRegion = encodeMeta(mainOffset, auxOffset)

            // If meta size changed, recalculate (rare edge case)
            val actualMainOffset = metaRegion.size
            val actualAuxOffset = actualMainOffset + mainRegion.size

            // Re-encode if offsets changed
            if (actualMainOffset != mainOffset || actualAuxOffset != auxOffset) {
                metaRegion = encodeMeta(actualMainOffset, actualAuxOffset)
            }

            // Determine aux space with padding to fill to spec limit
            val auxReservedSpace = if (auxRegion.isNotEmpty()) {
                auxRegion
            } else if (reserveAuxSpace) {
                // Reserve space for future aux data with an empty CBOR map plus zero padding
                val emptyMap = mapper.writeValueAsBytes(emptyMap<Int, Any>())
                val url = model.urlRecord?.url?.takeIf { it.isNotBlank() }
                val ndefOverhead = calculateNdefOverhead(url)
                val currentCborSize = metaRegion.size + mainRegion.size + emptyMap.size
                val availableCborSpace = SPEC_SIZE_LIMIT - ndefOverhead
                val paddingSize = maxOf(0, availableCborSpace - currentCborSize)
                emptyMap + ByteArray(paddingSize) { 0 }
            } else {
                ByteArray(0)
            }

            cborPayload = metaRegion + mainRegion + auxReservedSpace
        } else {
            // No meta region - just main region
            cborPayload = mainRegion
        }

        // 4. Build NDEF message - dual record if URL present, single record otherwise
        val url = model.urlRecord?.url?.takeIf { it.isNotBlank() }
        return if (url != null) {
            buildDualRecordNdef(cborPayload, url)
        } else {
            buildSingleRecordNdef(cborPayload)
        }
    }

    /**
     * Encode meta region with main and aux offsets
     */
    private fun encodeMeta(mainOffset: Int, auxOffset: Int): ByteArray {
        val data = mutableMapOf<Int, Any>()
        data[0] = mainOffset  // main_region_offset
        data[2] = auxOffset   // aux_region_offset
        return mapper.writeValueAsBytes(data)
    }

    /**
     * Calculate NDEF wrapper overhead based on record format
     */
    private fun calculateNdefOverhead(url: String?): Int {
        return if (url != null) {
            // Dual record: 8 prefix + 6 CBOR header + 28 MIME type + 4 URI header + 1 prefix + url.length + 1 terminator
            48 + url.removePrefix("https://www.").toByteArray(Charsets.UTF_8).size
        } else {
            // Single record: 8 prefix + 34 header + 1 terminator
            43
        }
    }

    /**
     * Build single-record NDEF message (CBOR only)
     */
    private fun buildSingleRecordNdef(cborPayload: ByteArray): ByteArray {
        val mimeType = "application/vnd.openprinttag"
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)

        val header = ByteBuffer.allocate(42)
        header.put(byteArrayOf(0xE1.toByte(), 0x40.toByte(), 0x27.toByte(), 0x01.toByte()))
        header.put(0x03.toByte()) // NDEF
        header.put(0xFF.toByte()) // Long length indicator

        val payloadLength = cborPayload.size + (header.capacity() - 8)
        header.putShort(payloadLength.toShort())

        header.put(0xC2.toByte()) // NDEF Record Header. MB=1, ME=1, TNF=2 (MIME), SR=0 (Long Record).
        header.put(mimeBytes.size.toByte())
        header.putInt(cborPayload.size)
        header.put(mimeBytes)

        val body = ByteBuffer.allocate(header.capacity() + cborPayload.size)
            .put(header.array())
            .put(cborPayload)
            .array()

        return ByteBuffer.allocate(body.size + 1)
            .put(body)
            .put(0xFE.toByte())
            .array()
    }

    /**
     * Build dual-record NDEF message (CBOR + URL)
     */
    private fun buildDualRecordNdef(cborPayload: ByteArray, urlString: String): ByteArray {
        // --- 1. Prepare URI Record ---
        // Using https://www. (prefix 0x02)
        val urlBody = urlString.removePrefix("https://www.")
        val urlPayload = byteArrayOf(0x02.toByte()) + urlBody.toByteArray(Charsets.UTF_8)

        // URI Record Header: MB=0, ME=1, TNF=0x01 (Well-Known), SR=1 (Short)
        // 0x51 = 01010001 (ME, SR, TNF=1)
        val uriRecord = ByteBuffer.allocate(3 + 1 + urlPayload.size).apply {
            put(0x51.toByte())               // Header
            put(0x01.toByte())               // Type Length ("U" is 1 byte)
            put(urlPayload.size.toByte())    // Payload Length (SR=1)
            put('U'.toByte())                // Type
            put(urlPayload)                  // Payload (Prefix + URL)
        }.array()

        // --- 2. Prepare CBOR Record ---
        val mimeBytes = "application/vnd.openprinttag".toByteArray(Charsets.US_ASCII)
        // CBOR Record Header: MB=1, ME=0, TNF=0x02 (MIME), SR=0 (Long)
        // 0x82 = 10000010 (MB, TNF=2)
        val cborRecord = ByteBuffer.allocate(1 + 1 + 4 + mimeBytes.size + cborPayload.size).apply {
            put(0x82.toByte())
            put(mimeBytes.size.toByte())
            putInt(cborPayload.size)
            put(mimeBytes)
            put(cborPayload)
        }.array()

        // --- 3. Assemble Full Message ---
        val totalNdefSize = cborRecord.size + uriRecord.size

        return ByteBuffer.allocate(8 + totalNdefSize + 1).apply {
            order(ByteOrder.BIG_ENDIAN)
            // NFC Prefix
            put(byteArrayOf(0xE1.toByte(), 0x40.toByte(), 0x27.toByte(), 0x01.toByte()))
            put(0x03.toByte())          // NDEF Tag
            put(0xFF.toByte())          // Long Length
            putShort(totalNdefSize.toShort())

            // Records
            put(cborRecord)
            put(uriRecord)

            // Terminator
            put(0xFE.toByte())
        }.array()
    }

    @Deprecated("Use serialize() instead, which now handles URL records automatically")
    fun generateDualRecordBin(cborPayload: ByteArray, urlString: String): ByteArray {
        return buildDualRecordNdef(cborPayload, urlString)
    }

    private fun encodeMain(m: OpenPrintTagModel): ByteArray {
        val data = mutableMapOf<Int, Any>()

        // === UUIDs (Keys 0-3) ===
        m.main.instance_uuid?.takeIf { it.isNotBlank() }?.let { data[0] = it }
        m.main.package_uuid?.takeIf { it.isNotBlank() }?.let { data[1] = it }
        m.main.material_uuid?.takeIf { it.isNotBlank() }?.let { data[2] = it }
        m.main.brand_uuid?.takeIf { it.isNotBlank() }?.let { data[3] = it }

        // === GTIN (Key 4) ===
        m.main.gtin?.toLongOrNull()?.let { data[4] = it }

        // === Brand-Specific IDs (Keys 5-7) ===
        m.main.brand_specific_instance_id?.takeIf { it.isNotBlank() }?.let { data[5] = it }
        m.main.brand_specific_package_id?.takeIf { it.isNotBlank() }?.let { data[6] = it }
        m.main.brand_specific_material_id?.takeIf { it.isNotBlank() }?.let { data[7] = it }

        // === Material Classification (Keys 8-11) ===
        classMap[m.main.material_class]?.let { data[8] = it }

        val typeStr = m.main.material_type
        if (typeStr != null && typeStr != "Select Material Type...") {
            typeMap[typeStr]?.let { data[9] = it }
        }

        m.main.material_name?.takeIf { it.isNotBlank() }?.let { data[10] = it }
        m.main.brand_name?.takeIf { it.isNotBlank() }?.let { data[11] = it }

        // === Write Protection (Key 13) ===
        // Note: could add writeProtectionMap lookup here if needed
        m.main.write_protection?.toIntOrNull()?.let { data[13] = it }

        // === Dates (Keys 14, 15) ===
        m.main.manufactured_date?.let {
            data[14] = getDateEpoch(m.main.manufactured_date)!!
        }
        m.main.expiration_date?.let {
            data[15] = getDateEpoch(m.main.expiration_date)!!
        }

        // === Weights (Keys 16-18) ===
        m.main.nominal_netto_full_weight?.let { data[16] = it }
        m.main.actual_netto_full_weight?.let { data[17] = it }
        m.main.empty_container_weight?.let { data[18] = it }

        // === Colors (Keys 19-24) ===
        m.main.primary_color?.takeIf { it.isNotBlank() }?.let { data[19] = it }
        m.main.secondary_color_0?.takeIf { it.isNotBlank() }?.let { data[20] = it }
        m.main.secondary_color_1?.takeIf { it.isNotBlank() }?.let { data[21] = it }
        m.main.secondary_color_2?.takeIf { it.isNotBlank() }?.let { data[22] = it }
        m.main.secondary_color_3?.takeIf { it.isNotBlank() }?.let { data[23] = it }
        m.main.secondary_color_4?.takeIf { it.isNotBlank() }?.let { data[24] = it }

        // === Optical Properties (Key 27) ===
        m.main.transmission_distance?.let { data[27] = it }

        // === Tags (Key 28) ===
        if (m.main.tags.isNotEmpty()) {
            val tagIds = m.main.tags.mapNotNull { tagsMap[it] }
            if (tagIds.isNotEmpty()) data[28] = tagIds
        }

        // === Physical Properties (Keys 29-33) ===
        m.main.density?.let { data[29] = it }
        m.main.filament_diameter?.let { data[30] = it }  // KEY 30! (not deprecated key 12)
        m.main.shore_hardness_a?.let { data[31] = it }
        m.main.shore_hardness_d?.let { data[32] = it }
        m.main.min_nozzle_diameter?.let { data[33] = it }

        // === Temperatures (Keys 34-41) ===
        m.main.min_print_temperature?.let { data[34] = it }
        m.main.max_print_temperature?.let { data[35] = it }
        m.main.preheat_temperature?.let { data[36] = it }
        m.main.min_bed_temperature?.let { data[37] = it }
        m.main.max_bed_temperature?.let { data[38] = it }
        m.main.min_chamber_temperature?.let { data[39] = it }
        m.main.max_chamber_temperature?.let { data[40] = it }
        m.main.chamber_temperature?.let { data[41] = it }

        // === Container Dimensions (Keys 42-45) ===
        m.main.container_width?.let { data[42] = it }
        m.main.container_outer_diameter?.let { data[43] = it }
        m.main.container_inner_diameter?.let { data[44] = it }
        m.main.container_hole_diameter?.let { data[45] = it }

        // === SLA-Specific Fields (Keys 46-51) ===
        m.main.viscosity_18c?.let { data[46] = it }
        m.main.viscosity_25c?.let { data[47] = it }
        m.main.viscosity_40c?.let { data[48] = it }
        m.main.viscosity_60c?.let { data[49] = it }
        m.main.container_volumetric_capacity?.let { data[50] = it }
        m.main.cure_wavelength?.let { data[51] = it }

        // === Material Abbreviation (Key 52) ===
        m.main.material_abbreviation?.takeIf { it.isNotBlank() }?.let { data[52] = it }

        // === Filament Length (Keys 53-54) ===
        m.main.nominal_full_length?.let { data[53] = it }
        m.main.actual_full_length?.let { data[54] = it }

        // === Country of Origin (Key 55) ===
        m.main.country_of_origin?.takeIf { it.isNotBlank() }?.let { data[55] = it }

        // === Certifications (Key 56) ===
        if (m.main.certifications.isNotEmpty()) {
            val certIds = m.main.certifications.mapNotNull { certsMap[it] }
            if (certIds.isNotEmpty()) data[56] = certIds
        }

        // === Drying Parameters (Keys 57-58) - FFF only ===
        m.main.drying_temperature?.let { data[57] = it }
        m.main.drying_time?.let { data[58] = it }

        return mapper.writeValueAsBytes(data)
    }

    private fun encodeAux(m: OpenPrintTagModel): ByteArray {
        val data = mutableMapOf<Int, Any>()

        // === Aux Region Fields (Keys 0-3) ===
        m.aux?.consumed_weight?.let { data[0] = it }
        m.aux?.workgroup?.takeIf { it.isNotBlank() }?.let { data[1] = it }
        m.aux?.general_purpose_range_user?.takeIf { it.isNotBlank() }?.let { data[2] = it }
        m.aux?.last_stir_time?.let {
            data[3] = getDateEpoch(it)!!
        }

        return if (data.isEmpty()) ByteArray(0) else mapper.writeValueAsBytes(data)
    }

    /**
     * Encode only the aux region for partial tag updates.
     * Returns raw CBOR bytes (no NDEF wrapper).
     */
    fun encodeAuxOnly(aux: AuxRegion?): ByteArray {
        val data = mutableMapOf<Int, Any>()

        aux?.consumed_weight?.let { data[0] = it }
        aux?.workgroup?.takeIf { it.isNotBlank() }?.let { data[1] = it }
        aux?.general_purpose_range_user?.takeIf { it.isNotBlank() }?.let { data[2] = it }
        aux?.last_stir_time?.let { data[3] = getDateEpoch(it)!! }

        return if (data.isEmpty()) ByteArray(0) else mapper.writeValueAsBytes(data)
    }

    /**
     * Parse a single NDEF record from the buffer and return structured data
     */
    private fun parseNextNdefRecord(buffer: ByteBuffer): NdefRecord {
        val header = buffer.get().toInt()

        // NDEF Header flags
        val tnf = header and 0x07 // Type Name Format
        val isShortRecord = (header and 0x10) != 0 // SR flag

        // Type Length
        val typeLength = buffer.get().toInt() and 0xFF

        // Payload Length (1 byte if SR=1, 4 bytes if SR=0)
        val payloadLength = if (isShortRecord) {
            buffer.get().toInt() and 0xFF
        } else {
            buffer.int
        }

        // ID Length (Only present if IL flag is set in header)
        val idLength = if ((header and 0x08) != 0) buffer.get().toInt() and 0xFF else 0

        // Read Type (e.g., "application/vnd.openprinttag" or "U" or "T")
        val typeBytes = ByteArray(typeLength)
        buffer.get(typeBytes)
        val typeString = String(typeBytes, Charsets.US_ASCII)

        // Skip ID if present
        if (idLength > 0) buffer.position(buffer.position() + idLength)

        // Extract Payload
        val payload = ByteArray(payloadLength)
        buffer.get(payload)

        // Route based on Type and return structured record
        return when {
            typeString == "application/vnd.openprinttag" -> {
                NdefRecord.CborRecord(payload)
            }
            tnf == 0x01 && typeString == "U" -> {
                NdefRecord.UriRecord(decodeUriRecord(payload))
            }
            else -> {
                NdefRecord.Unknown(typeString)
            }
        }
    }

    fun decodeUriRecord(payload: ByteArray): String {
        if (payload.isEmpty()) return ""

        val prefixCode = payload[0].toInt() and 0xFF
        val uriData = String(payload.copyOfRange(1, payload.size), Charsets.UTF_8)

        val prefix = when (prefixCode) {
            0x01 -> "http://www."
            0x02 -> "https://www."
            0x03 -> "http://"
            0x04 -> "https://"
            0x06 -> "mailto:"
            else -> "" // 0x00 or unknown
        }

        return prefix + uriData
    }

    /**
     * Main deserialization entry point - parses raw tag data into a model
     * Handles both single-record and dual-record (CBOR + URL) tags
     */
    fun deserialize(data: ByteArray): OpenPrintTagModel? {
        return try {
            val model = OpenPrintTagModel()
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

            // Skip NFC Magic Number (E1 40 27 01) if present
            if (data.size < 8) return model
            buffer.position(4)

            // Find the NDEF Message TLV (0x03)
            while (buffer.hasRemaining()) {
                val tag = buffer.get().toInt() and 0xFF
                if (tag == 0x03) {
                    // Found NDEF Message. Get its length.
                    val lengthByte = buffer.get().toInt() and 0xFF
                    val ndefMessageLength = if (lengthByte == 0xFF) buffer.short.toInt() and 0xFFFF else lengthByte

                    // Limit parsing to ONLY the NDEF Message content
                    val endOfMessage = buffer.position() + ndefMessageLength

                    // Loop through Records inside the Message
                    while (buffer.position() < endOfMessage && buffer.hasRemaining()) {
                        when (val record = parseNextNdefRecord(buffer)) {
                            is NdefRecord.CborRecord -> {
                                decodeRegions(record.payload, model)
                            }
                            is NdefRecord.UriRecord -> {
                                model.urlRecord = UrlRegion(url = record.url, includeInTag = true)
                            }
                            is NdefRecord.Unknown -> {
                                Log.d("Serializer", "Skipping unknown record type: ${record.type}")
                            }
                        }
                    }
                    break // Done parsing NDEF message
                } else if (tag == 0xFE) {
                    break // End of tag
                }
            }
            model
        } catch (e: Exception) {
            Log.e("Serializer", "Deserialize failed: ${e.message}")
            null
        }
    }

    /**
     * Deserialize with offset tracking for partial aux writes.
     * Returns DeserializeResult with aux region location info.
     */
    fun deserializeWithOffsets(data: ByteArray): DeserializeResult? {
        return try {
            val model = OpenPrintTagModel()
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            var cborPayloadOffset: Int? = null
            var auxByteOffset: Int? = null
            var auxByteSize: Int? = null

            if (data.size < 8) return DeserializeResult(model, null, null, null)
            buffer.position(4)

            while (buffer.hasRemaining()) {
                val tag = buffer.get().toInt() and 0xFF
                if (tag == 0x03) {
                    val lengthByte = buffer.get().toInt() and 0xFF
                    val ndefMessageLength = if (lengthByte == 0xFF) buffer.short.toInt() and 0xFFFF else lengthByte
                    val endOfMessage = buffer.position() + ndefMessageLength

                    while (buffer.position() < endOfMessage && buffer.hasRemaining()) {
                        val recordStartPos = buffer.position()
                        when (val record = parseNextNdefRecord(buffer)) {
                            is NdefRecord.CborRecord -> {
                                // Calculate where this CBOR payload starts in the original data
                                cborPayloadOffset = buffer.position() - record.payload.size
                                val offsets = decodeRegionsWithOffsets(record.payload, model)
                                if (offsets != null) {
                                    // Aux offset is relative to CBOR payload start
                                    auxByteOffset = cborPayloadOffset + offsets.first
                                    auxByteSize = offsets.second
                                }
                            }
                            is NdefRecord.UriRecord -> {
                                model.urlRecord = UrlRegion(url = record.url, includeInTag = true)
                            }
                            is NdefRecord.Unknown -> {
                                Log.d("Serializer", "Skipping unknown record type: ${record.type}")
                            }
                        }
                    }
                    break
                } else if (tag == 0xFE) {
                    break
                }
            }
            DeserializeResult(model, auxByteOffset, auxByteSize, cborPayloadOffset)
        } catch (e: Exception) {
            Log.e("Serializer", "DeserializeWithOffsets failed: ${e.message}")
            null
        }
    }

    /**
     * Decode regions and return aux region offset info.
     * Returns Pair(auxOffset, auxSize) relative to payload start, or null if no aux.
     */
    private fun decodeRegionsWithOffsets(payload: ByteArray, model: OpenPrintTagModel): Pair<Int, Int>? {
        val factory = CBORFactory()
        val parser = factory.createParser(payload)
        var auxOffset: Int? = null
        var auxSize: Int? = null

        try {
            var firstNode: JsonNode = mapper.readTree(parser) ?: return null

            if (isVersionMarker(firstNode)) {
                firstNode = mapper.readTree(parser) ?: return null
            }

            if (isMetaRegion(firstNode)) {
                val metaEndPosition = parser.currentLocation.byteOffset.toInt()
                val explicitMainOffset = if (firstNode.has("0")) firstNode.get("0").asInt() else null
                val explicitMainSize = if (firstNode.has("1")) firstNode.get("1").asInt() else null
                val explicitAuxOffset = if (firstNode.has("2")) firstNode.get("2").asInt() else null
                val explicitAuxSize = if (firstNode.has("3")) firstNode.get("3").asInt() else null

                val mainOffset = explicitMainOffset ?: metaEndPosition
                val mainSize = explicitMainSize
                    ?: (explicitAuxOffset?.minus(mainOffset) ?: (payload.size - mainOffset))

                if (mainSize > 0 && mainOffset + mainSize <= payload.size) {
                    val mainBytes = payload.copyOfRange(mainOffset, mainOffset + mainSize)
                    val mainNode = mapper.readTree(mainBytes)
                    if (mainNode != null) {
                        model.main = decodeMainRegionFromNode(mainNode)
                    }
                }

                if (explicitAuxOffset != null) {
                    auxOffset = explicitAuxOffset
                    auxSize = explicitAuxSize ?: (payload.size - explicitAuxOffset)

                    if (auxSize > 0 && explicitAuxOffset + auxSize <= payload.size) {
                        val auxBytes = payload.copyOfRange(explicitAuxOffset, explicitAuxOffset + auxSize)
                        val auxNode = mapper.readTree(auxBytes)
                        if (auxNode != null) {
                            model.aux = decodeAuxRegionFromNode(auxNode)
                        }
                    }
                }
            } else {
                // No meta region - sequential CBOR parsing
                model.main = decodeMainRegionFromNode(firstNode)
                val mainEndPos = parser.currentLocation.byteOffset.toInt()

                val secondNode: JsonNode? = mapper.readTree(parser)
                if (secondNode != null) {
                    auxOffset = mainEndPos
                    model.aux = decodeAuxRegionFromNode(secondNode)
                    auxSize = parser.currentLocation.byteOffset.toInt() - mainEndPos
                }
            }
        } catch (e: Exception) {
            Log.e("Serializer", "Decoding error: ${e.message}")
        } finally {
            parser.close()
        }

        return if (auxOffset != null && auxSize != null) Pair(auxOffset, auxSize) else null
    }

    fun decodeRegions(payload: ByteArray, model: OpenPrintTagModel) {
        val factory = CBORFactory()
        val parser = factory.createParser(payload)

        try {
            // 1. Read the first CBOR Object
            var firstNode: JsonNode = mapper.readTree(parser) ?: return

            // Check if this first node is a VERSION MARKER (small map with only key 2)
            // Format: {2: version_number} - skip it and read the next object
            if (isVersionMarker(firstNode)) {
                Log.d("Serializer", "Skipping version marker: ${firstNode}")
                firstNode = mapper.readTree(parser) ?: return
            }

            // Check if this first node is the META region
            // Meta region typically uses keys 0-3 for offsets/sizes
            // Main region uses keys 0-5+ for UUIDs, GTIN, etc.
            if (isMetaRegion(firstNode)) {
                // Get position where meta region ends (where main would start if offset not specified)
                val metaEndPosition = parser.currentLocation.byteOffset.toInt()

                // Get explicit values (null if missing)
                val explicitMainOffset = if (firstNode.has("0")) firstNode.get("0").asInt() else null
                val explicitMainSize = if (firstNode.has("1")) firstNode.get("1").asInt() else null
                val explicitAuxOffset = if (firstNode.has("2")) firstNode.get("2").asInt() else null
                val explicitAuxSize = if (firstNode.has("3")) firstNode.get("3").asInt() else null

                // Calculate main region bounds
                // If offset missing: region follows meta section
                // If size missing: region spans to aux offset or payload end
                val mainOffset = explicitMainOffset ?: metaEndPosition
                val mainSize = explicitMainSize
                    ?: (explicitAuxOffset?.minus(mainOffset) ?: (payload.size - mainOffset))

                // Decode main region using Jackson (consistent with non-meta path)
                if (mainSize > 0 && mainOffset + mainSize <= payload.size) {
                    val mainBytes = payload.copyOfRange(mainOffset, mainOffset + mainSize)
                    val mainNode = mapper.readTree(mainBytes)
                    if (mainNode != null) {
                        model.main = decodeMainRegionFromNode(mainNode)
                    }
                }

                // Calculate aux region bounds (only if aux_region_offset is specified)
                // Omitting aux_region_offset means aux region is not present
                if (explicitAuxOffset != null) {
                    // If size missing: aux spans to payload end
                    val auxSize = explicitAuxSize ?: (payload.size - explicitAuxOffset)

                    if (auxSize > 0 && explicitAuxOffset + auxSize <= payload.size) {
                        val auxBytes = payload.copyOfRange(explicitAuxOffset, explicitAuxOffset + auxSize)
                        val auxNode = mapper.readTree(auxBytes)
                        if (auxNode != null) {
                            model.aux = decodeAuxRegionFromNode(auxNode)
                        }
                    }
                }
            } else {
                // 2. Meta is OMITTED - First node is actually the Main Region
                model.main = decodeMainRegionFromNode(firstNode)

                // Try to read a second object (which would be the Aux Region)
                val secondNode: JsonNode? = mapper.readTree(parser)
                if (secondNode != null) {
                    model.aux = decodeAuxRegionFromNode(secondNode)
                }
            }
        } catch (e: Exception) {
            Log.e("Serializer", "Decoding error: ${e.message}")
        } finally {
            parser.close()
        }
    }

    /**
     * Check if a CBOR map is a version marker (format: {2: version_number})
     */
    private fun isVersionMarker(node: JsonNode): Boolean {
        if (!node.isObject) return false
        val fields = node.fieldNames().asSequence().toList()
        // Version marker has exactly one field with key "2"
        return fields.size == 1 && fields[0] == "2" && node.get("2")?.isInt == true
    }

    /**
     * Heuristic to check if a CBOR map is a Meta region vs Main region.
     * Meta region uses keys 0-3 for offsets/sizes (small integers).
     * Main region uses keys 0-3 for UUIDs (16-byte binary), not integers.
     */
    private fun isMetaRegion(node: JsonNode): Boolean {
        // Check if any of keys 0-3 are small integers - indicates meta region
        // Main region would have UUIDs (binary/string) at these keys, not integers
        for (key in 0..3) {
            val value = node.get(key.toString())
            if (value != null && value.isInt && value.asInt() < 1000) {
                return true
            }
        }
        return false
    }

    /**
     * Decode MainRegion directly from a Jackson JsonNode (more lenient with types)
     * Supports all fields from OpenPrintTag spec
     */
    private fun decodeMainRegionFromNode(node: JsonNode): MainRegion {
        val main = MainRegion()

        // Helper to get string from int, string, or bytes
        fun JsonNode?.asFlexibleString(): String? = when {
            this == null || this.isNull -> null
            this.isTextual -> this.asText()
            this.isNumber -> this.asText()
            this.isBinary -> this.binaryValue()?.let { bytes ->
                bytes.joinToString("") { "%02X".format(it) }
            }
            else -> this.asText()
        }

        // Helper to get int
        fun JsonNode?.asFlexibleInt(): Int? = when {
            this == null || this.isNull -> null
            this.isNumber -> this.asInt()
            this.isTextual -> this.asText().toIntOrNull()
            else -> null
        }

        // Helper to get float
        fun JsonNode?.asFlexibleFloat(): Float? = when {
            this == null || this.isNull -> null
            this.isNumber -> this.floatValue()
            this.isTextual -> this.asText().toFloatOrNull()
            else -> null
        }

        // === UUIDs (Keys 0-3) - stored as 16-byte binary ===
        main.instance_uuid = node.get("0").asFlexibleString()
        main.package_uuid = node.get("1").asFlexibleString()
        main.material_uuid = node.get("2").asFlexibleString()
        main.brand_uuid = node.get("3").asFlexibleString()

        // === GTIN (Key 4) ===
        main.gtin = node.get("4").asFlexibleString()

        // === Brand-Specific IDs (Keys 5-7) ===
        main.brand_specific_instance_id = node.get("5").asFlexibleString()
        main.brand_specific_package_id = node.get("6").asFlexibleString()
        main.brand_specific_material_id = node.get("7").asFlexibleString()

        // === Material Classification (Keys 8-11) ===
        main.material_class = node.get("8").asFlexibleString() ?: "FFF"
        main.material_type = node.get("9").asFlexibleString()
        main.material_name = node.get("10").asFlexibleString()
        main.brand_name = node.get("11").asFlexibleString()

        // === Write Protection (Key 13) - enum ===
        main.write_protection = node.get("13").asFlexibleString()

        // === Dates (Keys 14, 15) - epoch seconds ===
        node.get("14")?.let { dateNode ->
            if (dateNode.isNumber) {
                val epochSeconds = dateNode.asLong()
                main.manufactured_date = java.time.LocalDate.ofEpochDay(epochSeconds / 86400)
            }
        }
        node.get("15")?.let { dateNode ->
            if (dateNode.isNumber) {
                val epochSeconds = dateNode.asLong()
                main.expiration_date = java.time.LocalDate.ofEpochDay(epochSeconds / 86400)
            }
        }

        // === Weights (Keys 16-18) - Float per spec ===
        main.nominal_netto_full_weight = node.get("16").asFlexibleFloat()
        main.actual_netto_full_weight = node.get("17").asFlexibleFloat()
        main.empty_container_weight = node.get("18").asFlexibleFloat()

        // === Colors (Keys 19-24) - might be bytes or strings ===
        main.primary_color = node.get("19").asFlexibleString()
        main.secondary_color_0 = node.get("20").asFlexibleString()
        main.secondary_color_1 = node.get("21").asFlexibleString()
        main.secondary_color_2 = node.get("22").asFlexibleString()
        main.secondary_color_3 = node.get("23").asFlexibleString()
        main.secondary_color_4 = node.get("24").asFlexibleString()

        // === Optical Properties (Key 27) ===
        main.transmission_distance = node.get("27").asFlexibleFloat()

        // === Tags & Certifications (Keys 28, 56) - arrays ===
        node.get("28")?.let { tagsNode ->
            if (tagsNode.isArray) {
                main.tags = tagsNode.mapNotNull { it.asFlexibleString() }
            }
        }
        node.get("56")?.let { certsNode ->
            if (certsNode.isArray) {
                main.certifications = certsNode.mapNotNull { it.asFlexibleString() }
            }
        }

        // === Physical Properties (Keys 29-33) ===
        main.density = node.get("29").asFlexibleFloat()
        main.filament_diameter = node.get("30").asFlexibleFloat()  // KEY 30! (not deprecated key 12)
        main.shore_hardness_a = node.get("31").asFlexibleInt()
        main.shore_hardness_d = node.get("32").asFlexibleInt()
        main.min_nozzle_diameter = node.get("33").asFlexibleFloat()

        // === Temperatures (Keys 34-41) ===
        main.min_print_temperature = node.get("34").asFlexibleInt()
        main.max_print_temperature = node.get("35").asFlexibleInt()
        main.preheat_temperature = node.get("36").asFlexibleInt()
        main.min_bed_temperature = node.get("37").asFlexibleInt()
        main.max_bed_temperature = node.get("38").asFlexibleInt()
        main.min_chamber_temperature = node.get("39").asFlexibleInt()
        main.max_chamber_temperature = node.get("40").asFlexibleInt()
        main.chamber_temperature = node.get("41").asFlexibleInt()

        // === Container Dimensions (Keys 42-45) - FFF spool ===
        main.container_width = node.get("42").asFlexibleInt()
        main.container_outer_diameter = node.get("43").asFlexibleInt()
        main.container_inner_diameter = node.get("44").asFlexibleInt()
        main.container_hole_diameter = node.get("45").asFlexibleInt()

        // === SLA-Specific Fields (Keys 46-51) ===
        main.viscosity_18c = node.get("46").asFlexibleFloat()
        main.viscosity_25c = node.get("47").asFlexibleFloat()
        main.viscosity_40c = node.get("48").asFlexibleFloat()
        main.viscosity_60c = node.get("49").asFlexibleFloat()
        main.container_volumetric_capacity = node.get("50").asFlexibleFloat()
        main.cure_wavelength = node.get("51").asFlexibleInt()

        // === Material Abbreviation (Key 52) ===
        main.material_abbreviation = node.get("52").asFlexibleString()

        // === Filament Length (Keys 53-54) ===
        main.nominal_full_length = node.get("53").asFlexibleFloat()
        main.actual_full_length = node.get("54").asFlexibleFloat()

        // === Country of Origin (Key 55) ===
        main.country_of_origin = node.get("55").asFlexibleString()

        // === Drying Parameters (Keys 57-58) - FFF only ===
        main.drying_temperature = node.get("57").asFlexibleInt()
        main.drying_time = node.get("58").asFlexibleInt()

        // === Post-process enum values (reverse lookup) ===
        main.material_type = typeMap.entries.find {
            it.value.toString() == main.material_type
        }?.key ?: main.material_type

        main.material_class = classMap.entries.find {
            it.value.toString() == main.material_class
        }?.key ?: main.material_class

        main.tags = main.tags.map { tagId ->
            tagsMap.entries.find { it.value.toString() == tagId }?.key ?: tagId
        }

        main.certifications = main.certifications.map { certId ->
            certsMap.entries.find { it.value.toString() == certId }?.key ?: certId
        }

        return main
    }

    /**
     * Decode AuxRegion directly from a Jackson JsonNode
     * Supports all fields from OpenPrintTag spec
     */
    private fun decodeAuxRegionFromNode(node: JsonNode): AuxRegion {
        val aux = AuxRegion()

        // Helper functions (same as in decodeMainRegionFromNode)
        fun JsonNode?.asFlexibleString(): String? = when {
            this == null || this.isNull -> null
            this.isTextual -> this.asText()
            this.isNumber -> this.asText()
            this.isBinary -> this.binaryValue()?.let { bytes ->
                bytes.joinToString("") { "%02X".format(it) }
            }
            else -> this.asText()
        }

        fun JsonNode?.asFlexibleFloat(): Float? = when {
            this == null || this.isNull -> null
            this.isNumber -> this.floatValue()
            this.isTextual -> this.asText().toFloatOrNull()
            else -> null
        }

        // === Aux Region Fields (Keys 0-3) ===
        aux.consumed_weight = node.get("0").asFlexibleFloat()
        aux.workgroup = node.get("1").asFlexibleString()
        aux.general_purpose_range_user = node.get("2").asFlexibleString()

        // last_stir_time (key 3) - epoch seconds
        node.get("3")?.let { dateNode ->
            if (dateNode.isNumber) {
                val epochSeconds = dateNode.asLong()
                aux.last_stir_time = java.time.LocalDate.ofEpochDay(epochSeconds / 86400)
            }
        }

        return aux
    }
}