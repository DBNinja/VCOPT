package org.vcoprinttag

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import org.vcoprinttag.R
import org.vcoprinttag.databinding.ActivityGeneratorBinding
import org.vcoprinttag.model.OpenPrintTagModel
import org.vcoprinttag.model.UrlRegion
import org.vcoprinttag.model.Serializer
import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class OptionEntry(
    var key: Int = 0,
    var name: String = "",
    var category: String = "",
    var display_name: String = "",
    var description: String = "",
    var implies: List<String> = emptyList(),
    var hints: List<String> = emptyList(),
    var deprecated: String = ""
)

data class CertEntry(
    var key: Int = 0,
    var name: String = "",
    var display_name: String = "",
    var description: String = ""
)

data class CategoryMetadata(
    val name: String,
    val display_name: String,
    val emoji: String
)

class HintSpinnerAdapter(
    context: Context,
    resource: Int,
    objects: List<String>
) : ArrayAdapter<String>(context, resource, objects) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        view.alpha = if (position == 0) 0.6f else 1.0f
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent) as TextView
        if (position == 0) {
            view.alpha = 0.5f
        } else {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            view.setTextColor(ContextCompat.getColor(context, typedValue.resourceId))
            view.alpha = 1.0f
        }
        return view
    }
}

data class SelectionUpdate(
    val newSelectedKeys: Set<Int>,
    val suggestions: List<OptionEntry>
)

class SelectionManager(private val allOptions: List<OptionEntry>) {
    private val nameLookup = allOptions.associateBy { it.name }
    private val keyLookup = allOptions.associateBy { it.key }

    fun onOptionSelected(selectedKey: Int, currentSelection: Set<Int>): SelectionUpdate {
        val selectedOption = keyLookup[selectedKey] ?: return SelectionUpdate(currentSelection, emptyList())

        val newSelection = currentSelection.toMutableSet()
        val suggestions = mutableListOf<OptionEntry>()

        val stack = mutableListOf(selectedOption)
        while (stack.isNotEmpty()) {
            val current = stack.removeAt(0)
            if (newSelection.add(current.key)) {
                current.implies.forEach { name ->
                    nameLookup[name]?.let { stack.add(it) }
                }
            }
        }

        selectedOption.hints.forEach { hintName ->
            nameLookup[hintName]?.let { hintOption ->
                if (hintOption.key !in newSelection) {
                    suggestions.add(hintOption)
                }
            }
        }

        return SelectionUpdate(newSelection, suggestions)
    }
}

class GeneratorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGeneratorBinding
    private lateinit var selectionManager: SelectionManager

    private var currentSelectedTagKeys = mutableSetOf<Int>()
    private var currentSelectedCertKeys = mutableSetOf<Int>()
    private var allTagOptions: List<OptionEntry> = emptyList()
    private var allCertOptions: List<CertEntry> = emptyList()

    private var categoryMap = mapOf<String, CategoryMetadata>()

    private var classMap = mapOf<String, Int>()
    private var typeMap = mapOf<String, Int>()
    private var tagsMap = mapOf<String, Int>()
    private var certsMap = mapOf<String, Int>()

    // Date storage
    private var manufacturedDate: LocalDate? = null
    private var expirationDate: LocalDate? = null

    // Color storage (hex without #)
    private var primaryColorHex: String? = null
    private var secondaryColor0Hex: String? = null
    private var secondaryColor1Hex: String? = null
    private var secondaryColor2Hex: String? = null
    private var secondaryColor3Hex: String? = null
    private var secondaryColor4Hex: String? = null

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    sealed class TagDisplayItem {
        data class Header(val title: String) : TagDisplayItem()
        data class TagRow(val entry: OptionEntry) : TagDisplayItem()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGeneratorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle system insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Load YAML maps
        loadAllMaps()

        // Initialize selection manager
        selectionManager = SelectionManager(allTagOptions)

        // Setup dropdowns
        setupDropdowns()

        // Setup color pickers
        setupColorPickers()

        // Setup date pickers
        setupDatePickers()

        // Setup tag and certification buttons
        setupTagsAndCertsButtons()

        // Setup generate button
        setupGenerateButton()

        // Pre-fill UI if cached data exists
        val cachedData = intent.getByteArrayExtra("CACHED_TAG_DATA")
        if (cachedData != null) {
            val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
            val decodedModel = serializer.deserialize(cachedData)
            decodedModel?.let { preFillUI(it) }
        }
    }

    private fun loadAllMaps() {
        classMap = loadMapFromYaml("data/material_class_enum.yaml", "name")
        typeMap = loadMapFromYaml("data/material_type_enum.yaml", "abbreviation")
        tagsMap = loadMapFromYaml("data/tags_enum.yaml", "name")
        certsMap = loadMapFromYaml("data/material_certifications_enum.yaml", "display_name")

        loadTagsFromAssets()
        loadCertsFromAssets()
        loadMetadata()
    }

    private fun loadMapFromYaml(fileName: String, labelKey: String): Map<String, Int> {
        val resultMap = mutableMapOf<String, Int>()
        try {
            assets.open(fileName).use { inputStream ->
                val yaml = Yaml()
                val rawData = yaml.load<List<Map<String, Any>>>(inputStream)
                rawData?.forEach { entry ->
                    val label = entry[labelKey]?.toString()
                    val value = entry["key"]?.toString()?.toIntOrNull()
                    if (label != null && value != null) {
                        resultMap[label] = value
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Generator", "Error loading $fileName", e)
        }
        return resultMap
    }

    private fun loadMetadata() {
        try {
            assets.open("data/tag_categories_enum.yaml").use { catStream ->
                val catData: List<Map<String, Any>> = Yaml().load(catStream)
                categoryMap = catData.associate { map ->
                    val name = map["name"] as String
                    name to CategoryMetadata(
                        name = name,
                        display_name = map["display_name"] as String,
                        emoji = map["emoji"] as? String ?: ""
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadTagsFromAssets() {
        try {
            assets.open("data/tags_enum.yaml").use { inputStream ->
                val yaml = Yaml()
                val loadedData = yaml.load<List<Map<String, Any>>>(inputStream)

                allTagOptions = loadedData.filter { map ->
                    val isDeprecated = map["deprecated"] as? Boolean ?: false
                    !isDeprecated
                }.map { map ->
                    @Suppress("UNCHECKED_CAST")
                    OptionEntry(
                        key = (map["key"] as? Int) ?: 0,
                        name = (map["name"] as? String) ?: "",
                        category = (map["category"] as? String) ?: "",
                        display_name = (map["display_name"] as? String) ?: "",
                        description = (map["description"] as? String) ?: "",
                        implies = (map["implies"] as? List<String>) ?: emptyList(),
                        hints = (map["hints"] as? List<String>) ?: emptyList(),
                        deprecated = (map["deprecated"] as? String) ?: ""
                    )
                }
                selectionManager = SelectionManager(allTagOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.toast_failed_load_tags, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCertsFromAssets() {
        try {
            assets.open("data/material_certifications_enum.yaml").use { inputStream ->
                val yaml = Yaml()
                val loadedData = yaml.load<List<Map<String, Any>>>(inputStream)

                allCertOptions = loadedData.map { map ->
                    CertEntry(
                        key = (map["key"] as? Int) ?: 0,
                        name = (map["name"] as? String) ?: "",
                        display_name = (map["display_name"] as? String) ?: "",
                        description = (map["description"] as? String) ?: ""
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupDropdowns() {
        // Material Class dropdown
        val classAdapter = ArrayAdapter(this, R.layout.list_item, classMap.keys.toList())
        binding.autoCompleteMaterialClass.setAdapter(classAdapter)

        // Listen for material class changes to update field visibility
        binding.autoCompleteMaterialClass.setOnItemClickListener { _, _, _, _ ->
            updateFieldVisibilityForMaterialClass()
        }

        // Material Type dropdown
        val typeAdapter = ArrayAdapter(this, R.layout.list_item, typeMap.keys.toList().sorted())
        binding.autoCompleteMaterialType.setAdapter(typeAdapter)

        // Material Type validation
        binding.autoCompleteMaterialType.addTextChangedListener { text ->
            val input = text?.toString() ?: ""
            if (input.isNotEmpty() && !typeMap.containsKey(input)) {
                binding.layoutMaterialType.error = getString(R.string.error_unknown_material)
            } else {
                binding.layoutMaterialType.error = null
            }
        }

        // Write Protection dropdown
        val writeProtectionOptions = listOf("None", "Protected", "Locked")
        val wpAdapter = ArrayAdapter(this, R.layout.list_item, writeProtectionOptions)
        binding.autoCompleteWriteProtection.setAdapter(wpAdapter)

        // Set default visibility (FFF by default)
        updateFieldVisibilityForMaterialClass()
    }

    private fun updateFieldVisibilityForMaterialClass() {
        val materialClass = binding.autoCompleteMaterialClass.text?.toString() ?: "FFF"

        when (materialClass.uppercase()) {
            "SLA" -> {
                // SLA: Show SLA fields, hide FFF-specific fields
                binding.groupFffPhysical.visibility = View.GONE
                binding.groupFffTemperatures.visibility = View.GONE
                binding.groupFffContainer.visibility = View.GONE
                binding.groupSlaFields.visibility = View.VISIBLE
            }
            "SLS" -> {
                // SLS: Show temperatures (chamber temp relevant), hide filament-specific and SLA
                binding.groupFffPhysical.visibility = View.GONE
                binding.groupFffTemperatures.visibility = View.VISIBLE
                binding.groupFffContainer.visibility = View.GONE
                binding.groupSlaFields.visibility = View.GONE
            }
            else -> { // FFF (default)
                // FFF: Show FFF fields, hide SLA fields
                binding.groupFffPhysical.visibility = View.VISIBLE
                binding.groupFffTemperatures.visibility = View.VISIBLE
                binding.groupFffContainer.visibility = View.VISIBLE
                binding.groupSlaFields.visibility = View.GONE
            }
        }
    }

    private fun setupColorPickers() {
        // Primary color picker
        binding.colorButton.setOnClickListener {
            showColorPicker(binding.getColor, binding.colorButton) { hex ->
                primaryColorHex = hex
            }
        }

        binding.getColor.addTextChangedListener { text ->
            updateColorButtonFromText(text?.toString(), binding.colorButton)
            primaryColorHex = text?.toString()?.takeIf { it.length == 6 }
        }

        // Secondary color pickers
        setupSecondaryColorPicker(binding.colorButton0, binding.getSecondaryColor0) { hex -> secondaryColor0Hex = hex }
        setupSecondaryColorPicker(binding.colorButton1, binding.getSecondaryColor1) { hex -> secondaryColor1Hex = hex }
        setupSecondaryColorPicker(binding.colorButton2, binding.getSecondaryColor2) { hex -> secondaryColor2Hex = hex }
        setupSecondaryColorPicker(binding.colorButton3, binding.getSecondaryColor3) { hex -> secondaryColor3Hex = hex }
        setupSecondaryColorPicker(binding.colorButton4, binding.getSecondaryColor4) { hex -> secondaryColor4Hex = hex }
    }

    private fun setupSecondaryColorPicker(
        button: Button,
        editText: com.google.android.material.textfield.TextInputEditText,
        onColorChanged: (String?) -> Unit
    ) {
        button.setOnClickListener {
            showColorPicker(editText, button, onColorChanged)
        }
        editText.addTextChangedListener { text ->
            updateColorButtonFromText(text?.toString(), button)
            onColorChanged(text?.toString()?.takeIf { it.length == 6 })
        }
    }

    private fun showColorPicker(
        editText: com.google.android.material.textfield.TextInputEditText,
        button: Button,
        onColorChanged: (String?) -> Unit
    ) {
        ColorPickerDialog.Builder(this)
            .setTitle(getString(R.string.dialog_choose_color))
            .setPreferenceName("ColorPickerSettings")
            .setPositiveButton(getString(R.string.dialog_btn_confirm), object : ColorEnvelopeListener {
                override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
                    val hex = "%06X".format(0xFFFFFF and envelope.color)
                    editText.setText(hex)
                    button.backgroundTintList = ColorStateList.valueOf(envelope.color)
                    onColorChanged(hex)
                }
            })
            .attachBrightnessSlideBar(true)
            .attachAlphaSlideBar(false)
            .setNegativeButton(getString(R.string.dialog_btn_cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateColorButtonFromText(hexText: String?, button: Button) {
        if (hexText != null && hexText.length == 6) {
            try {
                val color = Color.parseColor("#$hexText")
                button.backgroundTintList = ColorStateList.valueOf(color)
            } catch (_: Exception) {
                // Invalid hex
            }
        }
    }

    private fun setupDatePickers() {
        binding.getManufacturedDate.setOnClickListener {
            showDatePicker(manufacturedDate) { date ->
                manufacturedDate = date
                binding.getManufacturedDate.setText(date.format(dateFormatter))
            }
        }

        binding.getExpirationDate.setOnClickListener {
            showDatePicker(expirationDate) { date ->
                expirationDate = date
                binding.getExpirationDate.setText(date.format(dateFormatter))
            }
        }
    }

    private fun showDatePicker(currentDate: LocalDate?, onDateSelected: (LocalDate) -> Unit) {
        val selection = currentDate?.let {
            it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } ?: MaterialDatePicker.todayInUtcMilliseconds()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(selection)
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            onDateSelected(date)
        }

        picker.show(supportFragmentManager, "date_picker")
    }

    private fun setupTagsAndCertsButtons() {
        binding.setTagsButton.setOnClickListener {
            if (allTagOptions.isEmpty()) {
                loadTagsFromAssets()
            }
            showTagSelectionPopup(getString(R.string.dialog_title_material_tags), allTagOptions)
        }

        binding.setCertificationsButton.setOnClickListener {
            showCertSelectionDialog()
        }
    }

    private fun showTagSelectionPopup(title: String, options: List<OptionEntry>) {
        val groupedItems = mutableListOf<TagDisplayItem>()
        val categories = options.groupBy { it.category }

        for ((categoryName, tags) in categories) {
            val displayName = categoryMap[categoryName]?.display_name
                ?: categoryName.replaceFirstChar { it.uppercase() }
            groupedItems.add(TagDisplayItem.Header(displayName))
            tags.forEach { groupedItems.add(TagDisplayItem.TagRow(it)) }
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.tag_category_select, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.options_recycler_view)
        val header = dialogView.findViewById<TextView>(R.id.category_header)
        header.text = title

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = TagsAdapter(groupedItems)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_btn_done) { _, _ ->
                updateTagChips()
            }
            .show()
    }

    private fun showCertSelectionDialog() {
        val displayNames = allCertOptions.map { it.display_name }.toTypedArray()
        val checkedItems = allCertOptions.map { it.key in currentSelectedCertKeys }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_certifications)
            .setMultiChoiceItems(displayNames, checkedItems) { _, which, isChecked ->
                val key = allCertOptions[which].key
                if (isChecked) {
                    currentSelectedCertKeys.add(key)
                } else {
                    currentSelectedCertKeys.remove(key)
                }
            }
            .setPositiveButton(R.string.dialog_btn_done) { _, _ ->
                updateCertChips()
            }
            .show()
    }

    private fun updateTagChips() {
        binding.chipGroupTags.removeAllViews()
        allTagOptions.filter { it.key in currentSelectedTagKeys }.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag.display_name
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    currentSelectedTagKeys.remove(tag.key)
                    updateTagChips()
                }
            }
            binding.chipGroupTags.addView(chip)
        }
    }

    private fun updateCertChips() {
        binding.chipGroupCertifications.removeAllViews()
        allCertOptions.filter { it.key in currentSelectedCertKeys }.forEach { cert ->
            val chip = Chip(this).apply {
                text = cert.display_name
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    currentSelectedCertKeys.remove(cert.key)
                    updateCertChips()
                }
            }
            binding.chipGroupCertifications.addView(chip)
        }
    }

    inner class TagsAdapter(private val items: List<TagDisplayItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_HEADER = 0
        private val TYPE_TAG = 1

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is TagDisplayItem.Header -> TYPE_HEADER
                is TagDisplayItem.TagRow -> TYPE_TAG
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderViewHolder(inflater.inflate(R.layout.tag_category_header, parent, false))
            } else {
                TagViewHolder(inflater.inflate(R.layout.tag_selectable_option, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (holder is HeaderViewHolder && item is TagDisplayItem.Header) {
                holder.title.text = item.title
            } else if (holder is TagViewHolder && item is TagDisplayItem.TagRow) {
                val tag = item.entry
                holder.title.text = tag.display_name
                holder.description.text = tag.description

                holder.checkBox.setOnCheckedChangeListener(null)
                holder.checkBox.isChecked = currentSelectedTagKeys.contains(tag.key)
                holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        val update = selectionManager.onOptionSelected(tag.key, currentSelectedTagKeys)
                        currentSelectedTagKeys.clear()
                        currentSelectedTagKeys.addAll(update.newSelectedKeys)

                        if (update.suggestions.isNotEmpty()) {
                            val names = update.suggestions.joinToString { it.display_name }
                            Toast.makeText(this@GeneratorActivity, getString(R.string.toast_consider_also, names), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        currentSelectedTagKeys.remove(tag.key)
                    }
                    notifyDataSetChanged()
                }
            }
        }

        override fun getItemCount() = items.size

        inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.category_header_title)
        }

        inner class TagViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.option_title)
            val description: TextView = view.findViewById(R.id.option_description)
            val checkBox: CheckBox = view.findViewById(R.id.option_checkbox)
        }
    }

    private fun setupGenerateButton() {
        binding.btnGenerateBin.setOnClickListener {
            val model = buildModelFromUI()
            val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)

            // If URL is provided, generate dual record (CBOR + URL)
            val bin = if (model.urlRecord?.url?.isNotBlank() == true) {
                val cborOnly = serializer.serialize(model, reserveAuxSpace = true)
                // Extract CBOR payload from the single-record bin
                val cborPayload = extractCborPayload(cborOnly)
                serializer.generateDualRecordBin(cborPayload, model.urlRecord!!.url)
            } else {
                serializer.serialize(model, reserveAuxSpace = true)
            }

            val resultIntent = Intent()
            resultIntent.putExtra("GEN_BIN_DATA", bin)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    /**
     * Extract raw CBOR payload from serialized NFC data (skipping NDEF headers)
     */
    private fun extractCborPayload(data: ByteArray): ByteArray {
        // Find NDEF message (0x03) and extract CBOR
        for (i in 4 until minOf(data.size, 20)) {
            if (data[i] == 0x03.toByte()) {
                val lengthByte = data[i + 1].toInt() and 0xFF
                val recordStart = if (lengthByte == 0xFF) i + 4 else i + 2

                // Parse record header
                val recordHeader = data[recordStart].toInt() and 0xFF
                val isShortRecord = (recordHeader and 0x10) != 0
                val typeLength = data[recordStart + 1].toInt() and 0xFF
                val payloadLengthOffset = recordStart + 2

                val payloadLength = if (isShortRecord) {
                    data[payloadLengthOffset].toInt() and 0xFF
                } else {
                    ((data[payloadLengthOffset].toInt() and 0xFF) shl 24) or
                    ((data[payloadLengthOffset + 1].toInt() and 0xFF) shl 16) or
                    ((data[payloadLengthOffset + 2].toInt() and 0xFF) shl 8) or
                    (data[payloadLengthOffset + 3].toInt() and 0xFF)
                }

                val payloadStart = if (isShortRecord) {
                    payloadLengthOffset + 1 + typeLength
                } else {
                    payloadLengthOffset + 4 + typeLength
                }

                return data.copyOfRange(payloadStart, payloadStart + payloadLength)
            }
        }
        return ByteArray(0)
    }

    private fun buildModelFromUI(): OpenPrintTagModel {
        val model = OpenPrintTagModel()
        val main = model.main

        // Identity
        main.brand_name = binding.getBrand.text?.toString()?.takeIf { it.isNotBlank() }
        main.material_name = binding.getMaterialName.text?.toString()?.takeIf { it.isNotBlank() }
        main.gtin = binding.getGtin.text?.toString()?.takeIf { it.isNotBlank() }
        main.country_of_origin = binding.getCountryOfOrigin.text?.toString()?.takeIf { it.isNotBlank() }

        // Material classification
        main.material_class = binding.autoCompleteMaterialClass.text?.toString()?.ifBlank { "FFF" } ?: "FFF"
        main.material_type = binding.autoCompleteMaterialType.text?.toString()?.takeIf { it.isNotBlank() }
        main.material_abbreviation = binding.getMaterialAbbrev.text?.toString()?.takeIf { it.isNotBlank() }

        // Colors
        main.primary_color = primaryColorHex
        main.secondary_color_0 = secondaryColor0Hex
        main.secondary_color_1 = secondaryColor1Hex
        main.secondary_color_2 = secondaryColor2Hex
        main.secondary_color_3 = secondaryColor3Hex
        main.secondary_color_4 = secondaryColor4Hex

        // Weights
        main.nominal_netto_full_weight = binding.getNominalWeight.text?.toString()?.toFloatOrNull()
        main.actual_netto_full_weight = binding.getActualWeight.text?.toString()?.toFloatOrNull()
        main.empty_container_weight = binding.getEmptyContainerWeight.text?.toString()?.toFloatOrNull()

        // Physical properties
        main.filament_diameter = binding.getFilamentDiameter.text?.toString()?.toFloatOrNull()
        main.density = binding.getDensity.text?.toString()?.toFloatOrNull()
        main.min_nozzle_diameter = binding.getMinNozzleDiameter.text?.toString()?.toFloatOrNull()
        main.shore_hardness_a = binding.getShoreHardnessA.text?.toString()?.toIntOrNull()
        main.shore_hardness_d = binding.getShoreHardnessD.text?.toString()?.toIntOrNull()
        main.nominal_full_length = binding.getNominalLength.text?.toString()?.toFloatOrNull()
        main.actual_full_length = binding.getActualLength.text?.toString()?.toFloatOrNull()

        // Temperatures
        main.min_print_temperature = binding.getMinTemp.text?.toString()?.toIntOrNull()
        main.max_print_temperature = binding.getMaxTemp.text?.toString()?.toIntOrNull()
        main.min_bed_temperature = binding.getMinBedTemp.text?.toString()?.toIntOrNull()
        main.max_bed_temperature = binding.getMaxBedTemp.text?.toString()?.toIntOrNull()
        main.min_chamber_temperature = binding.getMinChamberTemp.text?.toString()?.toIntOrNull()
        main.max_chamber_temperature = binding.getMaxChamberTemp.text?.toString()?.toIntOrNull()
        main.preheat_temperature = binding.getPreheatTemp.text?.toString()?.toIntOrNull()
        main.chamber_temperature = binding.getChamberTemp.text?.toString()?.toIntOrNull()

        // Container dimensions
        main.container_width = binding.getContainerWidth.text?.toString()?.toIntOrNull()
        main.container_outer_diameter = binding.getContainerOuterDiameter.text?.toString()?.toIntOrNull()
        main.container_inner_diameter = binding.getContainerInnerDiameter.text?.toString()?.toIntOrNull()
        main.container_hole_diameter = binding.getContainerHoleDiameter.text?.toString()?.toIntOrNull()

        // SLA properties
        main.viscosity_18c = binding.getViscosity18c.text?.toString()?.toFloatOrNull()
        main.viscosity_25c = binding.getViscosity25c.text?.toString()?.toFloatOrNull()
        main.viscosity_40c = binding.getViscosity40c.text?.toString()?.toFloatOrNull()
        main.viscosity_60c = binding.getViscosity60c.text?.toString()?.toFloatOrNull()
        main.cure_wavelength = binding.getCureWavelength.text?.toString()?.toIntOrNull()
        main.container_volumetric_capacity = binding.getContainerVolume.text?.toString()?.toFloatOrNull()

        // Dates
        main.manufactured_date = manufacturedDate
        main.expiration_date = expirationDate

        // UUIDs
        main.instance_uuid = binding.getInstanceUuid.text?.toString()?.takeIf { it.isNotBlank() }
        main.package_uuid = binding.getPackageUuid.text?.toString()?.takeIf { it.isNotBlank() }
        main.material_uuid = binding.getMaterialUuid.text?.toString()?.takeIf { it.isNotBlank() }
        main.brand_uuid = binding.getBrandUuid.text?.toString()?.takeIf { it.isNotBlank() }

        // Brand-specific IDs
        main.brand_specific_instance_id = binding.getBrandInstanceId.text?.toString()?.takeIf { it.isNotBlank() }
        main.brand_specific_package_id = binding.getBrandPackageId.text?.toString()?.takeIf { it.isNotBlank() }
        main.brand_specific_material_id = binding.getBrandMaterialId.text?.toString()?.takeIf { it.isNotBlank() }

        // Write protection
        main.write_protection = binding.autoCompleteWriteProtection.text?.toString()?.takeIf { it.isNotBlank() && it != "None" }

        // URL record
        binding.getUrl.text?.toString()?.takeIf { it.isNotBlank() }?.let { url ->
            model.urlRecord = UrlRegion(url = url, includeInTag = true)
        }

        // Tags and certifications
        main.tags = allTagOptions.filter { it.key in currentSelectedTagKeys }.map { it.name }
        main.certifications = allCertOptions.filter { it.key in currentSelectedCertKeys }.map { it.display_name }

        return model
    }

    private fun preFillUI(model: OpenPrintTagModel) {
        val main = model.main

        // Identity
        main.brand_name?.takeIf { it.isNotBlank() }?.let { binding.getBrand.setText(it) }
        main.material_name?.takeIf { it.isNotBlank() }?.let { binding.getMaterialName.setText(it) }
        main.gtin?.takeIf { it.isNotBlank() }?.let { binding.getGtin.setText(it) }
        main.country_of_origin?.takeIf { it.isNotBlank() }?.let { binding.getCountryOfOrigin.setText(it) }

        // Material classification
        main.material_class.takeIf { it.isNotBlank() }?.let { binding.autoCompleteMaterialClass.setText(it, false) }
        main.material_type?.takeIf { it.isNotBlank() }?.let { binding.autoCompleteMaterialType.setText(it, false) }
        main.material_abbreviation?.takeIf { it.isNotBlank() }?.let { binding.getMaterialAbbrev.setText(it) }

        // Colors
        main.primary_color?.takeIf { it.isNotBlank() }?.let { hex ->
            binding.getColor.setText(hex)
            primaryColorHex = hex
            updateColorButtonFromText(hex, binding.colorButton)
        }
        main.secondary_color_0?.let { setSecondaryColor(it, binding.getSecondaryColor0, binding.colorButton0) { secondaryColor0Hex = it } }
        main.secondary_color_1?.let { setSecondaryColor(it, binding.getSecondaryColor1, binding.colorButton1) { secondaryColor1Hex = it } }
        main.secondary_color_2?.let { setSecondaryColor(it, binding.getSecondaryColor2, binding.colorButton2) { secondaryColor2Hex = it } }
        main.secondary_color_3?.let { setSecondaryColor(it, binding.getSecondaryColor3, binding.colorButton3) { secondaryColor3Hex = it } }
        main.secondary_color_4?.let { setSecondaryColor(it, binding.getSecondaryColor4, binding.colorButton4) { secondaryColor4Hex = it } }

        // Weights
        main.nominal_netto_full_weight?.let { binding.getNominalWeight.setText(it.toString()) }
        main.actual_netto_full_weight?.let { binding.getActualWeight.setText(it.toString()) }
        main.empty_container_weight?.let { binding.getEmptyContainerWeight.setText(it.toString()) }

        // Physical properties
        main.filament_diameter?.let { binding.getFilamentDiameter.setText(it.toString()) }
        main.density?.let { binding.getDensity.setText(it.toString()) }
        main.min_nozzle_diameter?.let { binding.getMinNozzleDiameter.setText(it.toString()) }
        main.shore_hardness_a?.let { binding.getShoreHardnessA.setText(it.toString()) }
        main.shore_hardness_d?.let { binding.getShoreHardnessD.setText(it.toString()) }
        main.nominal_full_length?.let { binding.getNominalLength.setText(it.toString()) }
        main.actual_full_length?.let { binding.getActualLength.setText(it.toString()) }

        // Temperatures
        main.min_print_temperature?.let { binding.getMinTemp.setText(it.toString()) }
        main.max_print_temperature?.let { binding.getMaxTemp.setText(it.toString()) }
        main.min_bed_temperature?.let { binding.getMinBedTemp.setText(it.toString()) }
        main.max_bed_temperature?.let { binding.getMaxBedTemp.setText(it.toString()) }
        main.min_chamber_temperature?.let { binding.getMinChamberTemp.setText(it.toString()) }
        main.max_chamber_temperature?.let { binding.getMaxChamberTemp.setText(it.toString()) }
        main.preheat_temperature?.let { binding.getPreheatTemp.setText(it.toString()) }
        main.chamber_temperature?.let { binding.getChamberTemp.setText(it.toString()) }

        // Container dimensions
        main.container_width?.let { binding.getContainerWidth.setText(it.toString()) }
        main.container_outer_diameter?.let { binding.getContainerOuterDiameter.setText(it.toString()) }
        main.container_inner_diameter?.let { binding.getContainerInnerDiameter.setText(it.toString()) }
        main.container_hole_diameter?.let { binding.getContainerHoleDiameter.setText(it.toString()) }

        // SLA properties
        main.viscosity_18c?.let { binding.getViscosity18c.setText(it.toString()) }
        main.viscosity_25c?.let { binding.getViscosity25c.setText(it.toString()) }
        main.viscosity_40c?.let { binding.getViscosity40c.setText(it.toString()) }
        main.viscosity_60c?.let { binding.getViscosity60c.setText(it.toString()) }
        main.cure_wavelength?.let { binding.getCureWavelength.setText(it.toString()) }
        main.container_volumetric_capacity?.let { binding.getContainerVolume.setText(it.toString()) }

        // Dates
        main.manufactured_date?.let { date ->
            manufacturedDate = date
            binding.getManufacturedDate.setText(date.format(dateFormatter))
        }
        main.expiration_date?.let { date ->
            expirationDate = date
            binding.getExpirationDate.setText(date.format(dateFormatter))
        }

        // UUIDs
        main.instance_uuid?.let { binding.getInstanceUuid.setText(it) }
        main.package_uuid?.let { binding.getPackageUuid.setText(it) }
        main.material_uuid?.let { binding.getMaterialUuid.setText(it) }
        main.brand_uuid?.let { binding.getBrandUuid.setText(it) }

        // Brand-specific IDs
        main.brand_specific_instance_id?.let { binding.getBrandInstanceId.setText(it) }
        main.brand_specific_package_id?.let { binding.getBrandPackageId.setText(it) }
        main.brand_specific_material_id?.let { binding.getBrandMaterialId.setText(it) }

        // Write protection
        main.write_protection?.let { binding.autoCompleteWriteProtection.setText(it, false) }

        // Tags
        if (main.tags.isNotEmpty()) {
            currentSelectedTagKeys.clear()
            main.tags.forEach { name ->
                tagsMap[name]?.let { currentSelectedTagKeys.add(it) }
            }
            updateTagChips()
        }

        // Certifications
        if (main.certifications.isNotEmpty()) {
            currentSelectedCertKeys.clear()
            main.certifications.forEach { displayName ->
                certsMap[displayName]?.let { currentSelectedCertKeys.add(it) }
            }
            updateCertChips()
        }

        // URL
        model.urlRecord?.url?.takeIf { it.isNotBlank() }?.let {
            binding.getUrl.setText(it)
        }

        // Update field visibility based on loaded material class
        updateFieldVisibilityForMaterialClass()
    }

    private fun setSecondaryColor(
        hex: String,
        editText: com.google.android.material.textfield.TextInputEditText,
        button: Button,
        setter: () -> Unit
    ) {
        if (hex.isNotBlank()) {
            editText.setText(hex)
            updateColorButtonFromText(hex, button)
            setter()
        }
    }
}
