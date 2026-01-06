@file:OptIn(ExperimentalStdlibApi::class)

package org.openprinttag.model

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import org.openprinttag.util.ByteUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.time.ZoneOffset
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.ExperimentalSerializationApi

class Serializer(
    private val classMap: Map<String, Int>,
    private val typeMap: Map<String, Int>,
    private val tagsMap: Map<String, Int>,
    private val certsMap: Map<String, Int>
) {

    private val mapper = ObjectMapper(CBORFactory())

    fun serialize(model: OpenPrintTagModel): ByteArray {
        // 1. Encode regions as CBOR maps
        val mainRegion = encodeMain(model)
        val auxRegion = encodeAux(model)
        val metaRegion = ByteArray(0) // Reserved for future use

        //
        val header = ByteBuffer.allocate(42)
        header.put(byteArrayOf(0xE1.toByte(), 0x40.toByte(), 0x27.toByte(), 0x01.toByte()))
        //header.put("OPTG".toByteArray(StandardCharsets.US_ASCII))
        header.put(0x03.toByte()) // NDEF
        header.put(0xFF.toByte()) // NDEF
        //header.putShort(metaRegion.size.toShort())
        //header.putShort(mainRegion.size.toShort())
        

        // 3. Combine parts
        val payloadLength = metaRegion.size + mainRegion.size + auxRegion.size + (header.capacity() - 8)
        //6 bytes are used previously, 2 bytes for the short size
        header.putShort(payloadLength.toShort())

        val totalSize = metaRegion.size + mainRegion.size + auxRegion.size  + header.capacity()
        val cborSize = metaRegion.size + mainRegion.size + auxRegion.size 
        header.put(0xC2.toByte()) // NDEF Record Header. MB=1, ME=1, TNF=2 (MIME), SR=0 (Long Record).
        header.put(0x1C.toByte()) // 28 byes for MIME length

        header.putInt(cborSize.toInt())
        val mimeType = "application/vnd.openprinttag"
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
        header.put(mimeBytes)

        val body = ByteBuffer.allocate(totalSize)
            .put(header.array())
            .put(metaRegion)
            .put(mainRegion)
            .put(auxRegion)
            .array()

        // 4. Append CRC32 Checksum
        //val checksum = ByteUtils.crc32(body)
        return ByteBuffer.allocate(body.size + 1)
            .put(body)
            //.putInt(checksum)
            .put(0xFE.toByte())
            .array()
    }


    fun generateDualRecordBin(cborPayload: ByteArray, urlString: String): ByteArray {
        // --- 1. Prepare URI Record ---
        // Using https://www. (prefix 0x02)
        val urlBody = urlString.removePrefix("https://www.")
        val urlPayload = byteArrayOf(0x02.toByte()) + urlBody.toByteArray(Charsets.UTF_8)
        
        // URI Record Header: MB=0, ME=1, TNF=0x01 (Well-Known), SR=1 (Short)
        // 0x51 = 01010001 (ME, SR, TNF=1)
        val uriRecordHeader = ByteBuffer.allocate(3 + 1 + urlPayload.size).apply {
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
        val totalNdefSize = cborRecord.size + uriRecordHeader.size
        
        val finalBuffer = ByteBuffer.allocate(8 + totalNdefSize + 1).apply {
            order(ByteOrder.BIG_ENDIAN)
            // NFC Prefix
            put(byteArrayOf(0xE1.toByte(), 0x40.toByte(), 0x27.toByte(), 0x01.toByte()))
            put(0x03.toByte())          // NDEF Tag
            put(0xFF.toByte())          // Long Length
            putShort(totalNdefSize.toShort())
            
            // Records
            put(cborRecord)
            put(uriRecordHeader)
            
            // Terminator
            put(0xFE.toByte())
        }

        return finalBuffer.array()
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

    fun deserializeAllRecords(fileBytes: ByteArray) {
        val buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.BIG_ENDIAN)

        // --- 1. Skip NFC Magic Number (E1 40 27 01) ---
        buffer.position(4)

        // --- 2. Find the NDEF Message TLV (03) ---
        while (buffer.hasRemaining()) {
            val tag = buffer.get().toInt() and 0xFF
            if (tag == 0x03) {
                // Found NDEF Message. Get its length.
                val lengthByte = buffer.get().toInt() and 0xFF
                val ndefMessageLength = if (lengthByte == 0xFF) buffer.short.toInt() else lengthByte
                
                // Limit the buffer to ONLY the NDEF Message content
                val endOfMessage = buffer.position() + ndefMessageLength
                
                // --- 3. Loop through Records inside the Message ---
                while (buffer.position() < endOfMessage) {
                    parseNextNdefRecord(buffer)
                }
            } else if (tag == 0xFE) {
                break // End of tag
            }
        }
    }

    private fun parseNextNdefRecord(buffer: ByteBuffer) {
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

        // --- 4. Route based on Type ---
        when {
            typeString == "application/vnd.openprinttag" -> {
                println("Found CBOR Record: ${payload.toHexString()}")
                decodeCbor(payload)
            }
            tnf == 0x01 && typeString == "U" -> {
                val url = decodeUriRecord(payload)
                println("Found URL: $url")
            }
            else -> {
                println("Found Unknown Record Type: $typeString")
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
     * Decode CBOR payload into model (used internally by parseNextNdefRecord)
     */
    private fun decodeCbor(payload: ByteArray) {
        val model = OpenPrintTagModel()
        decodeRegions(payload, model)
        Log.d("Serializer", "Decoded CBOR: brand=${model.main.brand_name}, type=${model.main.material_type}")
    }

    /**
     * Main deserialization entry point - parses raw tag data into a model
     */
    fun deserialize(data: ByteArray): OpenPrintTagModel? {
        return try {
            val model = OpenPrintTagModel()
            // Skip NFC header bytes and find CBOR payload
            val cborStart = findCborPayloadStart(data)
            if (cborStart >= 0 && cborStart < data.size) {
                val payload = data.copyOfRange(cborStart, data.size - 1) // Exclude terminator
                decodeRegions(payload, model)
            }
            model
        } catch (e: Exception) {
            Log.e("Serializer", "Deserialize failed: ${e.message}")
            null
        }
    }

    /**
     * Find the start of CBOR payload by skipping NFC/NDEF headers
     */
    private fun findCborPayloadStart(data: ByteArray): Int {
        if (data.size < 8) return -1

        // Find NDEF message start (0x03) after NFC header
        for (i in 4 until minOf(data.size, 20)) {
            if (data[i] == 0x03.toByte()) {
                // Determine NDEF message length field size
                val lengthByte = data.getOrNull(i + 1)?.toInt()?.and(0xFF) ?: return -1
                val recordStart = if (lengthByte == 0xFF) i + 4 else i + 2

                if (recordStart >= data.size) return -1

                // Parse NDEF record header
                val recordHeader = data.getOrNull(recordStart)?.toInt()?.and(0xFF) ?: return -1
                val isShortRecord = (recordHeader and 0x10) != 0

                val typeLength = data.getOrNull(recordStart + 1)?.toInt()?.and(0xFF) ?: return -1

                // Payload length: 1 byte if SR=1, 4 bytes if SR=0
                val payloadLengthSize = if (isShortRecord) 1 else 4

                // CBOR payload starts after: record header (1) + type length (1) + payload length (1 or 4) + type string
                return recordStart + 1 + 1 + payloadLengthSize + typeLength
            }
        }
        return -1
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
                val mainOffset = firstNode.path(0).asInt(0)
                val mainSize = firstNode.path(1).asInt(0)
                val auxOffset = firstNode.path(2).asInt(0)
                val auxSize = firstNode.path(3).asInt(0)

                // Meta-Guided Slice
                if (mainSize > 0) {
                    val mainBytes = payload.copyOfRange(mainOffset, mainOffset + mainSize)
                    model.main = decodeMainRegion(mainBytes) ?: MainRegion()
                }
                if (auxSize > 0) {
                    val auxBytes = payload.copyOfRange(auxOffset, auxOffset + auxSize)
                    model.aux = decodeAuxRegion(auxBytes)
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
     * Meta region focuses on offsets (0-3), while Main focuses on material data.
     */
    private fun isMetaRegion(node: JsonNode): Boolean {
        // In Meta, key 0 is main_region_offset. 
        // In Main, key 0 is instance_uuid (usually a 16-byte binary).
        // If key 0 is a small integer, it's likely Meta.
        val key0 = node.get(0)
        return key0 != null && key0.isInt && key0.asInt() < 1000
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

    @OptIn(ExperimentalSerializationApi::class)
    fun decodeMainRegion(payload: ByteArray): MainRegion? {
        return try {
            // 1. Setup CBOR decoder
            val cbor = Cbor {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }

            // 2. Decode bytes into the MainRegion data class
            // Note: If your model properties are String but the tag has Int, 
            // this might need a custom serializer or use Jackson/Manual Map.
            val mainRegion = cbor.decodeFromByteArray<MainRegion>(payload)

            // 3. Post-process Enum values (Reverse Lookup)
            mainRegion.apply {
                // Convert Material Type: "0" (as string from CBOR) -> "PLA"
                material_type = typeMap.entries.find {
                    it.value.toString() == material_type
                }?.key ?: material_type

                // Convert Material Class: "0" -> "FFF"
                material_class = classMap.entries.find {
                    it.value.toString() == material_class
                }?.key ?: material_class

                // 4. Corrected Tags Mapping
                // We map the LIST of tags from the model, not the dictionary.
                tags = tags.map { tagId ->
                    // Look up the tagId (e.g., "52") in your YAML-loaded map
                    tagsMap.entries.find {
                        it.value.toString() == tagId
                    }?.key ?: tagId
                }

                // Do the same for certifications if applicable
                certifications = certifications.map { certId ->
                    certsMap.entries.find {
                        it.value.toString() == certId
                    }?.key ?: certId
                }
            }

            mainRegion
        } catch (e: Exception) {
            Log.e("Serializer", "Failed to decode Main Region: ${e.message}")
            null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun decodeAuxRegion(payload: ByteArray): AuxRegion? {
        return try {
            // 1. Setup the same CBOR configuration used for the Main region
            val cbor = Cbor {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }

            // 2. Decode the sliced bytes directly into the AuxRegion data class
            // This will map Key "0" to consumedWeight and Key "1" to workgroup
            val auxRegion = cbor.decodeFromByteArray<AuxRegion>(payload)

            // 3. Post-processing (if needed)
            // If you add fields in the future that use enums (like a 'status' field),
            // you would perform the reverse-lookup here just like in decodeMainRegion.
            
            auxRegion
        } catch (e: Exception) {
            Log.e("Serializer", "Failed to decode Aux Region: ${e.message}")
            null
        }
    }
}