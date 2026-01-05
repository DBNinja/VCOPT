package org.openprinttag

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
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.openprinttag.model.OpenPrintTagModel
import org.openprinttag.model.Serializer
import org.yaml.snakeyaml.Yaml
import org.openprinttag.app.R   // use the actual namespace where R was generated
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.ColorEnvelope
import java.io.InputStream
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.skydoves.colorpickerview.ColorPickerDialog
import org.openprinttag.app.databinding.ActivityGeneratorBinding
import kotlin.collections.emptyList


data class OptionEntry(
    var key: Int = 0,
    var name: String = "",
    var category: String = "",
    var display_name: String = "",
    var description: String = "",
    var implies: List<String> = emptyList<String>(),
    var hints: List<String> = emptyList<String>(),
    var deprecated: String = ""
)

data class RootConfig(
    val options: List<OptionEntry> = emptyList()
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
    // Helper to get the theme's primary text color
    private fun getThemeTextColor(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        return ContextCompat.getColor(context, typedValue.resourceId)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        // Closed state color
        if (position == 0) {
            view.alpha = 0.6f // Standard "hint" look (works in light and dark)
        } else {
            view.alpha = 1.0f
        }
        return view
    }


    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val tv = view as TextView
        if (position == 0) {
            // Hint color: use primary text with transparency or secondary color
            view.alpha = 0.5f
        } else {
            // Match the theme's primary text color
            view.setTextColor(getThemeTextColor(context))
            view.alpha = 1.0f
        }
        return view
    }
}

class TagsYamlLoader {
    fun loadFromResources(fileName: String): List<OptionEntry> {
        // Access the file from the resources folder
        val inputStream: InputStream? = this::class.java.classLoader.getResourceAsStream(fileName)
        
        requireNotNull(inputStream) { "Could not find file: $fileName" }
        
        val yaml = Yaml()
        val rawData: List<Map<String, Any>> = yaml.load(inputStream)
        
        return rawData.map { map ->
            OptionEntry(
                key = map["key"] as? Int ?: 0,
                name = map["name"] as? String ?: "",
                category = map["category"] as? String ?: "",
                display_name = map["display_name"] as? String ?: "",
                description = map["description"] as? String ?: "",
                implies = (map["implies"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                hints = (map["hints"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                deprecated = map["deprecated"] as? String ?: ""
            )
        }
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

        // 1. Process "Implies" (Recursive Auto-selection)
        val stack = mutableListOf(selectedOption)
        while (stack.isNotEmpty()) {
            val current = stack.removeAt(0)
            if (newSelection.add(current.key)) {
                current.implies.forEach { name ->
                    nameLookup[name]?.let { stack.add(it) }
                }
            }
        }

        // 2. Process "Hints" (Non-automatic)
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


    

    @SuppressLint("ClickableViewAccessibility")
    private lateinit var selectionManager: SelectionManager
    private var currentSelectedKeys = mutableSetOf<Int>()
    private var allOptions: List<OptionEntry> = emptyList()
    var color = 0x00
    var hex = "%06X".format(0xFFFFFF and color)

    private var categoryMap = mapOf<String, CategoryMetadata>()
    val yaml = org.yaml.snakeyaml.Yaml()


    private var classMap = mapOf<String, Int>()
    private var typeMap = mapOf<String, Int>()
    private var tagsMap = mapOf<String, Int>()
    private var certsMap = mapOf<String, Int>()

    private lateinit var autoCompleteMaterialType: MaterialAutoCompleteTextView
    private lateinit var autoCompleteMaterialClass: MaterialAutoCompleteTextView
    private lateinit var layoutMaterialType: TextInputLayout
    private lateinit var layoutMaterialClass: TextInputLayout





    private fun loadMaterialTypesFromYaml(assetPath: String, fieldName: String): List<String> {
        val names = mutableListOf<String>()
        try {
            val inputStream = assets.open(assetPath)
            val rawData: List<Any>? = org.yaml.snakeyaml.Yaml().load(inputStream)

            rawData?.forEach { entry ->
                if (entry is Map<*, *>) {
                    val value = entry[fieldName] as? String
                    if (value != null) {
                        names.add(value)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return names.sorted() // Optional: Sort alphabetically for the UI
    }

    private fun loadMetadata() {
        try {
            // Load Categories for Emojis
            val catStream: InputStream = assets.open("data/tag_categories_enum.yaml")
            val catData: List<Map<String, Any>> = Yaml().load(catStream)
            categoryMap = catData.associate { map ->
                val name = map["name"] as String
                name to CategoryMetadata(
                    name = name,
                    display_name = map["display_name"] as String,
                    emoji = map["emoji"] as? String ?: "📦"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    sealed class TagDisplayItem {
        data class Header(val title: String) : TagDisplayItem()
        data class TagRow(val entry: OptionEntry) : TagDisplayItem()
    }

    private fun loadMapFromYaml(fileName: String, labelKey: String): Map<String, Int> {
        val resultMap = mutableMapOf<String, Int>()
        try {
            val inputStream = assets.open(fileName)
            val yaml = org.yaml.snakeyaml.Yaml()
            val rawData = yaml.load<List<Map<String, Any>>>(inputStream)

            rawData?.forEach { entry ->
                val label = entry[labelKey]?.toString()
                val value = entry["key"]?.toString()?.toIntOrNull()

                if (label != null && value != null) {
                    resultMap[label] = value
                }
            }
            inputStream.close()
            android.util.Log.d("YAML", "Loaded ${resultMap.size} items from $fileName")
        } catch (e: Exception) {
            android.util.Log.e("YAML", "Error loading $fileName", e)
        }
        return resultMap
    }
    private fun preFillUI(model: OpenPrintTagModel) {
        // Basic Fields
        val main = model.main
        // 1. Strings: Only set if not null and not blank
        main.brandName?.takeIf { it.isNotBlank() }?.let {
            findViewById<EditText>(R.id.getBrand).setText(it)
        }
        main.materialName?.takeIf { it.isNotBlank() }?.let { 
            findViewById<EditText>(R.id.getMaterialName).setText(it) 
        }

        main.primaryColor?.takeIf { it.isNotBlank() }?.let { colorHex ->
            findViewById<EditText>(R.id.getColor).setText(colorHex)
            // If you have a color preview button, update it here too
            try {
                val colorInt = Color.parseColor("#$colorHex")
                findViewById<Button>(R.id.colorButton).backgroundTintList = 
                    ColorStateList.valueOf(colorInt)
            } catch (e: Exception) { /* Invalid hex */ }
        }

        main.gtin?.takeIf { it.isNotBlank() }?.let { 
                findViewById<EditText>(R.id.getGtin).setText(it) 
        }

        // Dropdowns
        // materialType and materialClass are stored as Strings in the model after Serializer.decode
        main.materialType?.let { typeName ->
        val typeView = findViewById<MaterialAutoCompleteTextView>(R.id.autoCompleteMaterialType)
        typeView.setText(typeName, false) 
        }

        main.materialClass?.let { className ->
            val classView = findViewById<MaterialAutoCompleteTextView>(R.id.autoCompleteMaterialClass)
            classView.setText(className, false)
        }

        main.minPrintTemp?.let { findViewById<EditText>(R.id.getMinTemp).setText(it.toString()) }
        main.maxPrintTemp?.let { findViewById<EditText>(R.id.getMaxTemp).setText(it.toString()) }


        // 4. Multi-select Tags
        if (main.materialTags.isNotEmpty()) {
            val recyclerView = findViewById<RecyclerView>(R.id.options_recycler_view)
            val adapter = recyclerView.adapter as? OptionsAdapter
            adapter?.updateSelectedTags(main.materialTags)
        }


    }


    inner class OptionsAdapter(private val items: List<TagDisplayItem>) :
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
                // Reuse the TextView from your category_select.xml or create a small header layout
                val view = inflater.inflate(R.layout.tag_category_header, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = inflater.inflate(R.layout.tag_selectable_option, parent, false)
                TagViewHolder(view)
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
                holder.checkBox.isChecked = currentSelectedKeys.contains(tag.key)
                holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        // FIX: You must assign the result of the selection manager back to the state
                        val update = selectionManager.onOptionSelected(tag.key, currentSelectedKeys)
                        currentSelectedKeys.clear()
                        currentSelectedKeys.addAll(update.newSelectedKeys)

                        // Optional: Show a snackbar if there are suggestions/hints
                        if (update.suggestions.isNotEmpty()) {
                            val names = update.suggestions.joinToString { it.display_name }
                            Toast.makeText(this@GeneratorActivity, "Consider also: $names", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        currentSelectedKeys.remove(tag.key)
                    }
                    notifyDataSetChanged() // Refreshes implies/dependencies
                }
            }
        }
        fun updateSelectedTags(namesFromTag: List<String>) {
            // 1. Clear current selection
            currentSelectedKeys.clear()

            // 2. Map String names (e.g., "esd_safe") to Integer keys (e.g., 8)
            namesFromTag.forEach { name ->
                val key = tagsMap[name]
                if (key != null) {
                    currentSelectedKeys.add(key)
                }
            }

            // 3. Notify the adapter to refresh the checkboxes
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        // Two distinct ViewHolders
        inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.category_header_title)
        }

        inner class TagViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.option_title)
            val description: TextView = view.findViewById(R.id.option_description)
            val checkBox: CheckBox = view.findViewById(R.id.option_checkbox)
        }
    }
    
    private fun handleSelectionUpdate(newKeys: Set<Int>, suggestions: List<OptionEntry>) {
        // Update your app's global state
        currentSelectedKeys = newKeys.toMutableSet()

        // Show hints for each suggested item
        suggestions.forEach { hint ->
            Snackbar.make(findViewById(android.R.id.content),
                "Suggestion: ${hint.display_name}", Snackbar.LENGTH_LONG)
                .setAction("Add") {
                    // Logic to select the hinted item if they click 'Add'
                }
                .show()
        }
    }

    fun showSelectionPopup(categoryName: String, options: List<OptionEntry>) {

        val groupedItems = mutableListOf<TagDisplayItem>()
        val categories = options.groupBy { it.category }

        val builder = AlertDialog.Builder(this)
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.tag_category_select, null)

        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.options_recycler_view)
        val header = dialogView.findViewById<TextView>(R.id.category_header)

        header.text = categoryName

        for ((categoryName, tags) in categories) {
            // Add the Header (you could map 'biological' to 'Biological Properties' here)
            groupedItems.add(TagDisplayItem.Header(categoryName.replaceFirstChar { it.uppercase() }))

            // Add all tags belonging to this category
            tags.forEach { groupedItems.add(TagDisplayItem.TagRow(it)) }
        }

        // Set up RecyclerView with an adapter using your OptionEntry list
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = OptionsAdapter(groupedItems)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .show()
    }


    fun showColorPickerDialog() {
        val getColor = findViewById<EditText>(R.id.getColor)
        val colorButton = findViewById<Button>(R.id.colorButton)

        ColorPickerDialog.Builder(this)
        .setTitle("Choose Color")
        .setPreferenceName("MyColorPickerSettings") // Remembers the last color picked
        .setPositiveButton("Confirm", object : ColorEnvelopeListener {
            override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
                val selectedColor = envelope.color
                val hexCode = envelope.hexCode
                
                // Do something with the color (e.g., change a view's background)
                // R.setBackgroundColor(selectedColor)
                color = selectedColor
                hex = "%06X".format(0xFFFFFF and color)
                getColor.setText(hex)
                colorButton.backgroundTintList = ColorStateList.valueOf(color)
            }
        })
        .attachBrightnessSlideBar(true) // Optional: Add brightness slider
        .attachAlphaSlideBar(false)
        .setNegativeButton("Cancel") { dialogInterface, _ -> 
            dialogInterface.dismiss() 
        }
        //.bottomSpace(12) // Space between sliders and buttons

        .show()

    }
    private fun loadTagsFromAssets() {
        try {
            // Access the asset manager directly from the Activity context
            val inputStream: InputStream = assets.open("data/tags_enum.yaml")

            val yaml = Yaml()
            // Load the list of maps from YAML
            val loadedData = yaml.load<List<Map<String, Any>>>(inputStream)

            // Map the raw YAML data to your OptionEntry objects
            allOptions = loadedData .filter { map ->
                // Exclude if 'deprecated' is explicitly true
                val isDeprecated = map["deprecated"] as? Boolean ?: false
                !isDeprecated
            } .map { map ->
                OptionEntry(
                    key = (map["key"] as? Int) ?: 0,
                    name = (map["name"] as? String) ?: "",
                    category = (map["category"] as? String) ?: "",
                    display_name = (map["display_name"] as? String) ?: "",
                    description = (map["description"] as? String) ?: "",
                    // Handle the 'implies' list if it exists
                    implies = (map["implies"] as? List<String>) ?: emptyList(),
                    hints = (map["hints"] as? List<String>) ?: emptyList(),
                    deprecated =  (map["deprecated"] as? String) ?: ""
                )
            }

            // Initialize the selection manager with the loaded options
            selectionManager = SelectionManager(allOptions)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load tags from assets", Toast.LENGTH_SHORT).show()
        }
    }



    private lateinit var binding: ActivityGeneratorBinding
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate and set the root
        binding = ActivityGeneratorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fix the "Camera Notch" overlap
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        classMap = loadMapFromYaml("data/material_class_enum.yaml", "name")
        typeMap  = loadMapFromYaml("data/material_type_enum.yaml", "abbreviation")
        tagsMap  = loadMapFromYaml("data/tags_enum.yaml", "name")
        certsMap = loadMapFromYaml("data/material_certifications_enum.yaml", "display_name")



        selectionManager = SelectionManager(allOptions)
        val getBrand = findViewById<EditText>(R.id.getBrand)
        val getMaterialName = findViewById<EditText>(R.id.getMaterialName)
        val getColor = findViewById<EditText>(R.id.getColor)
        val getGtin = findViewById<EditText>(R.id.getGtin)
        val getMin = findViewById<EditText>(R.id.getMinTemp)
        val getMax = findViewById<EditText>(R.id.getMaxTemp)
        var model = OpenPrintTagModel()


        val cachedData = intent.getByteArrayExtra("CACHED_TAG_DATA")
        if (cachedData != null) {
            // 1. Initialize your Serializer
            val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
            
            // 2. Decode the bytes into your Model
            val decodedModel = serializer.deserialize(cachedData)
            
            // 3. Pre-fill the UI
            decodedModel?.let { preFillUI(it) }
        }

        autoCompleteMaterialType = findViewById<MaterialAutoCompleteTextView>(R.id.autoCompleteMaterialType)
        autoCompleteMaterialClass = findViewById<MaterialAutoCompleteTextView>(R.id.autoCompleteMaterialClass)
        val btnGenerate = findViewById<Button>(R.id.btnGenerateBin)
        btnGenerate.setOnClickListener {
            val selectedTagNames = allOptions
                .filter { it.key in currentSelectedKeys }
                .map { it.name }

            model.main.brandName = getBrand.text.toString()
            model.main.materialName = getMaterialName.text.toString()
            model.main.primaryColor = getColor.text.toString()
            model.main.gtin = getGtin.text.toString().ifBlank { null }
            model.main.minPrintTemp = getMin.text.toString().toIntOrNull()
            model.main.maxPrintTemp = getMax.text.toString().toIntOrNull()
            model.main.materialType = autoCompleteMaterialType.text.toString()
            model.main.materialClass = autoCompleteMaterialClass.text.toString().ifBlank { "FFF" }
            model.main.materialTags = currentSelectedKeys.map { key ->
                allOptions.find { it.key == key }?.name ?: ""
            }



            // 2. Instantiate Serializer with dynamic maps
            val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)

            // 3. Serialize
            val bin = serializer.serialize(model)
            val resultIntent = Intent()

            val typedValue = autoCompleteMaterialType.text.toString()

            resultIntent.putExtra("GEN_BIN_DATA", bin)
            setResult(Activity.RESULT_OK, resultIntent)

            finish()
        }


        // 1. Load the abbreviations from material_type_enum.yaml
        val materialTypes = loadMaterialTypesFromYaml("data/material_type_enum.yaml", "abbreviation").toMutableList()

        // Create the Adapter
        val adapter = ArrayAdapter(this, R.layout.list_item, materialTypes)
        autoCompleteMaterialType.setAdapter(adapter)
        autoCompleteMaterialType.setOnClickListener {
            autoCompleteMaterialType.showDropDown()
        }

        // 3. Handle Selection
        autoCompleteMaterialType.setOnItemClickListener { parent, _, position, _ ->
            val selectedType = parent.getItemAtPosition(position) as String

        }
        val layoutMaterialType = findViewById<TextInputLayout>(R.id.layoutMaterialType)

        // Handle when the user types or clicks the (X) button
        autoCompleteMaterialType.addTextChangedListener { text ->
            val input = text?.toString() ?: ""
            if (input.isNotEmpty() && !typeMap.containsKey(input)) {
                layoutMaterialType.error = "Unknown Material"
            } else {
                layoutMaterialType.error = null // Everything is valid
            }




            if (input.isEmpty()) {
                model.main.materialType = null
            } else {
                // We update the model, but we will validate against
                // our typeMap during the "Generate" click.
                model.main.materialType = input
            }
        }

        val colorButton = findViewById<Button>(R.id.colorButton)
        colorButton.setOnClickListener {
            showColorPickerDialog()
        }

        val setTagsButton = findViewById<Button>(R.id.setTagsButton)
        setTagsButton.setOnClickListener {
            // 1. Load all options from the YAML if not already loaded
            if (allOptions.isEmpty()) {
                loadTagsFromAssets()
            }

            // 3. Show the selection popup
            // You could further group these by 'category' from the YAML for a better UX
            showSelectionPopup("Material Tags", allOptions)

        }


        setupDropdowns()

    }


    private fun setupDropdowns() {
        // 1. Material Type Dropdown
        val typeAdapter = ArrayAdapter(
            this,
            R.layout.list_item,
            typeMap.keys.toList() // Extract just the names (PLA, ABS, etc.)
        )
        autoCompleteMaterialType.setAdapter(typeAdapter)

        // 2. Material Class Dropdown
        val classAdapter = ArrayAdapter(
            this,
            R.layout.list_item,
            classMap.keys.toList()
        )
        autoCompleteMaterialClass.setAdapter(classAdapter)

        // 3. Tag Type Dropdown
        val tagAdapter = ArrayAdapter(
            this,
            R.layout.list_item,
            tagsMap.keys.toList()
        )
    }
}
