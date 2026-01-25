package com.drunkenblindninja.vcoprinttag

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.drunkenblindninja.vcoprinttag.databinding.ActivityMainBinding
import com.drunkenblindninja.vcoprinttag.model.OpenPrintTagModel
import com.drunkenblindninja.vcoprinttag.model.AuxRegion
import com.drunkenblindninja.vcoprinttag.model.Serializer
import com.drunkenblindninja.vcoprinttag.ui.TagDataAdapter
import com.drunkenblindninja.vcoprinttag.ui.TagDisplayBuilder

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private var cachedTagData: ByteArray? = null
    private var isWriteMode = false
    private var isAuxWriteMode = false  // For aux-only writes
    private var pendingAuxData: ByteArray? = null
    private var pendingAuxOffset: Int = -1

    // Cached deserialized model for aux editing
    private var cachedModel: OpenPrintTagModel? = null
    private var cachedAuxOffset: Int? = null

    // Tag data adapter
    private lateinit var tagDataAdapter: TagDataAdapter

    // NFC Reader Mode flags
    // FLAG_READER_SKIP_NDEF_CHECK improves compatibility with Samsung devices
    private val readerModeFlags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

    // Reader mode extras bundle - presence check delay helps with Samsung devices
    private val readerModeExtras = Bundle().apply {
        putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
    }

    // Enum maps for deserialization (loaded lazily)
    private var classMap: Map<String, Int> = emptyMap()
    private var typeMap: Map<String, Int> = emptyMap()
    private var tagsMap: Map<String, Int> = emptyMap()
    private var certsMap: Map<String, Int> = emptyMap()
    private var mapsLoaded = false

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveCachedDataToFile(uri)
            }
        }
    }

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadBytesFromFile(uri)
            }
        }
    }

    private val generatorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data?.getByteArrayExtra("GEN_BIN_DATA")
            if (data != null) {
                cachedTagData = data
                binding.tvStatus.text = getString(R.string.status_new_bin_cached)
                checkSize(cachedTagData?.size  )
                // Try to decode and display the generated data
                lifecycleScope.launch(Dispatchers.IO) {
                    ensureMapsLoaded()
                    val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
                    // Use deserializeWithOffsets to get aux region location
                    val result = serializer.deserializeWithOffsets(data)
                    val decodedModel = result?.model

                    withContext(Dispatchers.Main) {
                        cachedModel = decodedModel
                        cachedAuxOffset = result?.auxByteOffset
                        displayTagData(decodedModel)
                        Toast.makeText(this@MainActivity, R.string.toast_data_ready_to_write, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val auxEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult

            // Build AuxRegion from result
            val aux = AuxRegion(
                consumed_weight = if (data.hasExtra(AuxEditorActivity.RESULT_CONSUMED_WEIGHT))
                    data.getFloatExtra(AuxEditorActivity.RESULT_CONSUMED_WEIGHT, 0f) else null,
                workgroup = data.getStringExtra(AuxEditorActivity.RESULT_WORKGROUP),
                general_purpose_range_user = data.getStringExtra(AuxEditorActivity.RESULT_USER_DATA),
                last_stir_time = if (data.hasExtra(AuxEditorActivity.RESULT_LAST_STIR_TIME))
                    java.time.LocalDate.ofEpochDay(data.getLongExtra(AuxEditorActivity.RESULT_LAST_STIR_TIME, 0))
                else null
            )

            val auxOffset = data.getIntExtra(AuxEditorActivity.RESULT_AUX_OFFSET, -1)

            // Encode aux region
            lifecycleScope.launch(Dispatchers.IO) {
                ensureMapsLoaded()
                val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
                val auxBytes = serializer.encodeAuxOnly(aux)

                withContext(Dispatchers.Main) {
                    if (auxOffset > 0 && auxBytes.isNotEmpty()) {
                        // Partial write mode
                        pendingAuxData = auxBytes
                        pendingAuxOffset = auxOffset
                        isAuxWriteMode = true
                        isWriteMode = false
                        updateModeIndicator()
                        Toast.makeText(this@MainActivity, "Tap tag to update aux region", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.toast_no_aux_offset, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    companion object {
        const val SPEC_SIZE_LIMIT = 316
        const val HARD_SIZE_LIMIT = 504
    }

    fun checkSize(binSize: Int?)
    {
        binSize?.let {

            if (binSize > SPEC_SIZE_LIMIT) {
                // Show a non-blocking warning (e.g., a Snackbar or Dialog)
                Toast.makeText(this,"Warning: File size (${binSize} bytes) exceeds the OpenPrintTag spec of $SPEC_SIZE_LIMIT bytes.",Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        // Initialize RecyclerView adapter
        tagDataAdapter = TagDataAdapter()
        binding.rvTagData.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = tagDataAdapter
        }

        // Setup toolbar with menu
        setSupportActionBar(binding.toolbar)

        // Show empty state initially
        displayTagData(null)

        // Initialize NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, R.string.error_nfc_not_available, Toast.LENGTH_LONG).show()
        } else if (!nfcAdapter!!.isEnabled) {
            // Prompt user to enable NFC
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_nfc_disabled)
                .setMessage(R.string.dialog_message_nfc_disabled)
                .setPositiveButton(R.string.dialog_btn_settings) { _, _ ->
                    startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
                }
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .show()
        }

        // Setup click listeners
        setupClickListeners()

        // Initialize mode indicator
        updateModeIndicator()

        // Handle NFC intent that launched the activity (cold start)
        if (savedInstanceState == null) {
            handleNfcIntent(intent)
        }
    }

    private fun setupClickListeners() {
        binding.btnLaunchGenerator.setOnClickListener {
            launchGenerator()
        }

        binding.btnSaveBin.setOnClickListener {
            saveBinFile()
        }

        binding.btnLoadBin.setOnClickListener {
            loadBinFile()
        }

        binding.btnReadTag.setOnClickListener {
            isWriteMode = false
            isAuxWriteMode = false
            updateModeIndicator()
            Toast.makeText(this, R.string.toast_ready_to_read, Toast.LENGTH_SHORT).show()
        }

        binding.btnWriteTag.setOnClickListener {
            isWriteMode = true
            isAuxWriteMode = false
            updateModeIndicator()
            Toast.makeText(this, R.string.toast_ready_to_write, Toast.LENGTH_SHORT).show()
        }

        binding.btnEditAux.setOnClickListener {
            launchAuxEditor()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_generator -> {
                launchGenerator()
                true
            }
            R.id.action_load -> {
                loadBinFile()
                true
            }
            R.id.action_save -> {
                saveBinFile()
                true
            }
            R.id.action_credits -> {
                startActivity(Intent(this, CreditsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun launchGenerator() {
        val intent = Intent(this, GeneratorActivity::class.java)
        cachedTagData?.let { data ->
            intent.putExtra("CACHED_TAG_DATA", data)
        }
        generatorLauncher.launch(intent)
    }

    private fun launchAuxEditor() {
        if (cachedTagData == null) {
            Toast.makeText(this, R.string.toast_read_tag_first, Toast.LENGTH_SHORT).show()
            return
        }

        if (cachedAuxOffset == null || cachedAuxOffset!! <= 0) {
            // Show confirmation dialog to add aux region
            showAddAuxRegionDialog()
            return
        }

        launchAuxEditorActivity()
    }

    private fun showAddAuxRegionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_add_aux)
            .setMessage(R.string.dialog_message_add_aux)
            .setNegativeButton(R.string.dialog_btn_cancel, null)
            .setPositiveButton(R.string.dialog_btn_add) { _, _ ->
                addAuxRegionAndLaunchEditor()
            }
            .show()
    }

    private fun addAuxRegionAndLaunchEditor() {
        val model = cachedModel ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            ensureMapsLoaded()
            val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)

            // Add empty aux region to the model
            model.aux = AuxRegion()

            // Re-serialize with aux region reserved (handles URL automatically)
            val newData = serializer.serialize(model, reserveAuxSpace = true)

            // Get the new aux offset
            val result = serializer.deserializeWithOffsets(newData)

            withContext(Dispatchers.Main) {
                cachedTagData = newData
                cachedModel = result?.model
                cachedAuxOffset = result?.auxByteOffset

                // Update display
                displayTagData(cachedModel)
                binding.tvStatus.text = getString(R.string.status_new_bin_cached)

                // Enable write mode for full tag rewrite
                isWriteMode = true
                isAuxWriteMode = false
                updateModeIndicator()

                // Launch aux editor
                launchAuxEditorActivity()
            }
        }
    }

    private fun launchAuxEditorActivity() {
        val intent = Intent(this, AuxEditorActivity::class.java).apply {
            // Pass existing aux data
            cachedModel?.aux?.let { aux ->
                aux.consumed_weight?.let { putExtra(AuxEditorActivity.EXTRA_CONSUMED_WEIGHT, it) }
                aux.workgroup?.let { putExtra(AuxEditorActivity.EXTRA_WORKGROUP, it) }
                aux.general_purpose_range_user?.let { putExtra(AuxEditorActivity.EXTRA_USER_DATA, it) }
                aux.last_stir_time?.let { putExtra(AuxEditorActivity.EXTRA_LAST_STIR_TIME, it.toEpochDay()) }
            }
            // Pass weight data from main region for calculator
            cachedModel?.main?.let { main ->
                // Use actual weight if available, otherwise nominal
                val fullWeight = main.actual_netto_full_weight ?: main.nominal_netto_full_weight
                fullWeight?.let { putExtra(AuxEditorActivity.EXTRA_FULL_WEIGHT, it) }
                main.empty_container_weight?.let { putExtra(AuxEditorActivity.EXTRA_EMPTY_CONTAINER_WEIGHT, it) }
            }
            // Pass aux offset for partial write
            putExtra(AuxEditorActivity.EXTRA_AUX_BYTE_OFFSET, cachedAuxOffset!!)
        }
        auxEditorLauncher.launch(intent)
    }

    private fun loadBinFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
        }
        openFileLauncher.launch(intent)
    }

    private fun saveBinFile() {
        if (cachedTagData == null) {
            Toast.makeText(this, R.string.toast_read_tag_first, Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "my_tag_data.bin")
            }
            createFileLauncher.launch(intent)
        }
    }

    private fun displayTagData(model: OpenPrintTagModel?) {
        val items = TagDisplayBuilder.buildDisplayItems(model)
        tagDataAdapter.updateItems(items)
    }

    private fun updateModeIndicator() {
        when {
            isAuxWriteMode -> {
                binding.chipMode.text = "AUX WRITE"
                binding.chipMode.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_write)
                )
                binding.chipMode.setTextColor(ContextCompat.getColor(this, R.color.mode_write_text))
            }
            isWriteMode -> {
                binding.chipMode.text = "WRITE MODE"
                binding.chipMode.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_write)
                )
                binding.chipMode.setTextColor(ContextCompat.getColor(this, R.color.mode_write_text))
            }
            else -> {
                binding.chipMode.text = "READ MODE"
                binding.chipMode.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_read)
                )
                binding.chipMode.setTextColor(ContextCompat.getColor(this, R.color.mode_read_text))
            }
        }
    }

    private fun loadBytesFromFile(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()

                    ensureMapsLoaded()
                    val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
                    // Use deserializeWithOffsets to get aux region location
                    val result = serializer.deserializeWithOffsets(bytes)
                    val decodedModel = result?.model

                    withContext(Dispatchers.Main) {
                        cachedTagData = bytes
                        cachedModel = decodedModel
                        cachedAuxOffset = result?.auxByteOffset
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = getString(R.string.toast_file_loaded)
                        displayTagData(decodedModel)
                        Toast.makeText(this@MainActivity, R.string.toast_file_loaded, Toast.LENGTH_SHORT).show()
                    }
                    checkSize(cachedTagData?.size)
                }
            } catch (e: Exception) {
                Log.e("NFC_APP", "Failed to load file", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun readAndDisplayTag(tag: Tag) {
        val manager = NfcHelper(tag)

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val data = manager.readFullTag()

                var decodedModel: OpenPrintTagModel? = null
                var auxOffset: Int? = null

                if (data != null) {
                    ensureMapsLoaded()
                    val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
                    // Use deserializeWithOffsets to get aux region location
                    val result = serializer.deserializeWithOffsets(data)
                    decodedModel = result?.model
                    auxOffset = result?.auxByteOffset
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (data != null) {
                        cachedTagData = data
                        cachedModel = decodedModel
                        cachedAuxOffset = auxOffset
                        binding.tvStatus.text = getString(R.string.status_tag_read_success, data.size)
                        displayTagData(decodedModel)
                        checkSize(cachedTagData?.size)
                    } else {
                        binding.tvStatus.text = getString(R.string.status_read_failed)
                    }
                }
            } catch (e: Exception) {
                Log.e("NFC", "Read failed", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = getString(R.string.error_nfc_operation_failed, e.message ?: "Unknown error")
                    Toast.makeText(this@MainActivity, R.string.toast_read_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun writeMemoryToTag(tag: Tag): Boolean = withContext(Dispatchers.IO) {
        val dataToWrite = cachedTagData ?: return@withContext false
        val manager = NfcHelper(tag)
        manager.writeFullTag(dataToWrite)
    }

    private fun saveCachedDataToFile(uri: Uri) {
        val dataToSave = cachedTagData ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.write(dataToSave)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.toast_saved_from_memory, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NFC", "Save failed", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(this, this, readerModeFlags, readerModeExtras)
        Log.d("NFC", "Reader mode enabled with flags: $readerModeFlags")
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
        Log.d("NFC", "Reader mode disabled")
    }

    // NfcAdapter.ReaderCallback implementation
    override fun onTagDiscovered(tag: Tag) {
        Log.d("NFC", "onTagDiscovered called")
        runOnUiThread {
            handleTagDiscovered(tag)
        }
    }

    private fun handleTagDiscovered(tag: Tag) {
        when {
            isAuxWriteMode -> {
                // Partial aux write mode
                binding.progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    try {
                        val success = writeAuxToTag(tag)
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            if (success) {
                                Toast.makeText(this@MainActivity, R.string.toast_aux_update_complete, Toast.LENGTH_SHORT).show()
                                isAuxWriteMode = false
                                pendingAuxData = null
                                pendingAuxOffset = -1
                                updateModeIndicator()
                                // Re-read tag to show updated data
                                readAndDisplayTag(tag)
                            } else {
                                Toast.makeText(this@MainActivity, R.string.toast_aux_update_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("NFC", "Aux write failed", e)
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            binding.tvStatus.text = getString(R.string.error_nfc_operation_failed, e.message ?: "Unknown error")
                            Toast.makeText(this@MainActivity, R.string.toast_aux_update_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            isWriteMode -> {
                binding.progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    try {
                        val success = writeMemoryToTag(tag)
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            if (success) {
                                Toast.makeText(this@MainActivity, R.string.toast_write_complete, Toast.LENGTH_SHORT).show()
                                isWriteMode = false
                                updateModeIndicator()
                            } else {
                                Toast.makeText(this@MainActivity, R.string.toast_write_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("NFC", "Write failed", e)
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            binding.tvStatus.text = getString(R.string.error_nfc_operation_failed, e.message ?: "Unknown error")
                            Toast.makeText(this@MainActivity, R.string.toast_write_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            else -> {
                readAndDisplayTag(tag)
            }
        }
    }

    // Handle NFC intents for cold starts (app launched via NFC)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("NFC", "onNewIntent called with action: ${intent.action}")
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {

            Log.d("NFC", "handleNfcIntent processing NFC action: ${intent.action}")
            @Suppress("DEPRECATION")
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let { handleTagDiscovered(it) }
        }
    }

    private suspend fun writeAuxToTag(tag: Tag): Boolean = withContext(Dispatchers.IO) {
        val auxData = pendingAuxData ?: return@withContext false
        val auxOffset = pendingAuxOffset
        if (auxOffset <= 0) return@withContext false

        val manager = NfcHelper(tag)
        manager.writeAtOffset(auxOffset, auxData)
    }

    private fun ensureMapsLoaded() {
        if (mapsLoaded) return
        classMap = loadMapFromYaml("data/material_class_enum.yaml", "name")
        typeMap = loadMapFromYaml("data/material_type_enum.yaml", "abbreviation")
        tagsMap = loadMapFromYaml("data/tags_enum.yaml", "name")
        certsMap = loadMapFromYaml("data/material_certifications_enum.yaml", "display_name")
        mapsLoaded = true
    }

    private fun loadMapFromYaml(fileName: String, labelKey: String): Map<String, Int> {
        val resultMap = mutableMapOf<String, Int>()
        try {
            assets.open(fileName).use { inputStream ->
                val yaml = org.yaml.snakeyaml.Yaml()
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
            Log.e("MainActivity", "Error loading $fileName", e)
        }
        return resultMap
    }

}
