package org.openprinttag

import android.app.Activity
import android.app.PendingIntent
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
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openprinttag.app.R
import org.openprinttag.app.databinding.ActivityMainBinding
import org.openprinttag.model.OpenPrintTagModel
import org.openprinttag.model.Serializer
import org.openprinttag.ui.TagDataAdapter
import org.openprinttag.ui.TagDisplayBuilder
import org.openprinttag.ui.TagDisplayItem

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private var cachedTagData: ByteArray? = null
    private var detectedTag: Tag? = null
    private var isWriteMode = false

    // Tag data adapter
    private lateinit var tagDataAdapter: TagDataAdapter

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
                    val decodedModel = serializer.deserialize(data)

                    withContext(Dispatchers.Main) {
                        displayTagData(decodedModel)
                        Toast.makeText(this@MainActivity, R.string.toast_data_ready_to_write, Toast.LENGTH_SHORT).show()
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
        }

        // Setup click listeners
        setupClickListeners()

        // Initialize mode indicator
        updateModeIndicator()
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
            updateModeIndicator()
            Toast.makeText(this, R.string.toast_ready_to_read, Toast.LENGTH_SHORT).show()
        }

        binding.btnWriteTag.setOnClickListener {
            isWriteMode = true
            updateModeIndicator()
            Toast.makeText(this, R.string.toast_ready_to_write, Toast.LENGTH_SHORT).show()
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
        if (isWriteMode) {
            binding.chipMode.text = "WRITE MODE"
            binding.chipMode.chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.status_write)
            )
            binding.chipMode.setTextColor(ContextCompat.getColor(this, R.color.mode_write_text))
        } else {
            binding.chipMode.text = "READ MODE"
            binding.chipMode.chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.status_read)
            )
            binding.chipMode.setTextColor(ContextCompat.getColor(this, R.color.mode_read_text))
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
                    val decodedModel = serializer.deserialize(bytes)

                    withContext(Dispatchers.Main) {
                        cachedTagData = bytes
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

                val decodedModel: OpenPrintTagModel? = if (data != null) {
                    ensureMapsLoaded()
                    val serializer = Serializer(classMap, typeMap, tagsMap, certsMap)
                    serializer.deserialize(data)
                } else null

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (data != null) {
                        cachedTagData = data
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
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

            @Suppress("DEPRECATION")
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)

            tag?.let {
                detectedTag = it

                if (isWriteMode) {
                    binding.progressBar.visibility = View.VISIBLE
                    lifecycleScope.launch {
                        try {
                            val success = writeMemoryToTag(it)
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
                } else {
                    readAndDisplayTag(it)
                }
            }
        }
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

    fun ByteArray.toHexString(): String {
        return joinToString(" ") { "%02X".format(it) }
    }
}
