// Placeholder to ensure file exists; actual content provided earlier in the conversation.
package org.openprinttag

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import org.openprinttag.app.R   // use the actual namespace where R was generated
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openprinttag.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var tvStatus: TextView
    private lateinit var tvTagInfo: TextView
    private lateinit var tvModeIndicator: TextView
    private lateinit var progressBar: ProgressBar
    private var cachedTagData: ByteArray? = null // This is your "Memory" storage

    // In your Activity
    private var detectedTag: Tag? = null

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Now we have the file URI, let's read the tag and write to it
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
                // 2. Update cachedTagData
                cachedTagData = data

                // 3. Update UI to reflect the new data is ready to be written
                cachedTagData?.let { cachedTagData ->
                    // 'data' is the non-null ByteArray
                    tvTagInfo.text = cachedTagData.toHexString()
                    tvStatus.text = getString(R.string.status_new_bin_cached)
                } ?: run {
                    tvTagInfo.text = getString(R.string.status_no_data_cached)
                    tvStatus.text = getString(R.string.status_no_data_cached)
                }
                //tvTagInfo.text = cachedTagData.toHexString()
                //tvStatus.text = "Status: New bin generated and cached"
                Toast.makeText(this, R.string.toast_data_ready_to_write, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadBytesFromFile(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Open the file stream
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    // 2. Read all bytes into memory
                    val bytes = inputStream.readBytes()

                    withContext(Dispatchers.Main) {
                        // 3. Update your "Memory" variable
                        cachedTagData = bytes

                        // 4. Update the UI so you can see the hex
                        val dataDisplay: TextView = findViewById(R.id.tvTagInfo)
                        dataDisplay.text = bytes.toHexString()

                        Toast.makeText(this@MainActivity, R.string.toast_file_loaded, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("NFC_APP", "Failed to load file", e)
            }
        }
    }

    private fun triggerFileSave() {
        if (detectedTag == null) {
            Toast.makeText(this, R.string.toast_no_tag_detected, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream" // Binary file type
            putExtra(Intent.EXTRA_TITLE, "ntag_backup.bin")
        }
        createFileLauncher.launch(intent)
    }
    fun ByteArray.toHexString(): String {
        return joinToString(" ") { "%02X".format(it) }
    }
    // In your Activity

    private fun readAndDisplayTag(tag: Tag) {
        val manager = NfcHelper(tag)
        val statusText: TextView = findViewById(R.id.tvStatus)
        val dataDisplay: TextView = findViewById(R.id.tvTagInfo)

        // Show loading indicator
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val data = manager.readFullTag()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (data != null) {
                        cachedTagData = data // Save to memory
                        statusText.text = getString(R.string.status_tag_read_success, data.size)

                        // Convert to Hex and display
                        dataDisplay.text = data.toHexString()
                    } else {
                        statusText.text = getString(R.string.status_read_failed)
                    }
                }
            } catch (e: Exception) {
                Log.e("NFC", "Read failed", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = getString(R.string.error_nfc_operation_failed, e.message ?: "Unknown error")
                    Toast.makeText(this@MainActivity, R.string.toast_write_failed, Toast.LENGTH_SHORT).show()
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
        val dataToSave = cachedTagData ?: return // Get from memory

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

        // Listen for any tag discovery
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        // Stop listening so other apps can use NFC
        nfcAdapter?.disableForegroundDispatch(this)
    }

    private var isWriteMode = false // Toggle this with a button

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Check if the intent is actually an NFC discovery
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)

            tag?.let {
                // Store the detected tag for file save operations
                detectedTag = it

                if (isWriteMode) {
                    // EXECUTE WRITE
                    progressBar.visibility = View.VISIBLE
                    lifecycleScope.launch {
                        try {
                            val success = writeMemoryToTag(it)
                            withContext(Dispatchers.Main) {
                                progressBar.visibility = View.GONE
                                if (success) {
                                    Toast.makeText(this@MainActivity, R.string.toast_write_complete, Toast.LENGTH_SHORT).show()
                                    isWriteMode = false // Turn off write mode after success
                                    updateModeIndicator()
                                } else {
                                    Toast.makeText(this@MainActivity, R.string.toast_write_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("NFC", "Write failed", e)
                            withContext(Dispatchers.Main) {
                                progressBar.visibility = View.GONE
                                tvStatus.text = getString(R.string.error_nfc_operation_failed, e.message ?: "Unknown error")
                                Toast.makeText(this@MainActivity, R.string.toast_write_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    // EXECUTE READ (Default behavior)
                    readAndDisplayTag(it)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply padding so the UI stays below the "notch"/camera
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        // INITIALIZE ALL VIEWS FIRST
        tvStatus = findViewById(R.id.tvStatus)
        tvTagInfo = findViewById(R.id.tvTagInfo)
        tvModeIndicator = findViewById(R.id.tvModeIndicator)
        progressBar = findViewById(R.id.progressBar)

        val btnLaunchGenerator = requireNotNull(
            findViewById<Button>(R.id.btnLaunchGenerator)
        ) { "btnLaunchGenerator not found in activity_main.xml" }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Check if NFC is available
        if (nfcAdapter == null) {
            Toast.makeText(this, R.string.error_nfc_not_available, Toast.LENGTH_LONG).show()
        }

        btnLaunchGenerator.setOnClickListener {
            // Toast.makeText(this, "Works", Toast.LENGTH_LONG).show()
            val intent = Intent(this, GeneratorActivity::class.java)

            cachedTagData?.let { data ->
                intent.putExtra("CACHED_TAG_DATA", data)
            }
            
            generatorLauncher.launch(intent)
        }

        val saveButton: Button = findViewById(R.id.btnSaveBin)

        saveButton.setOnClickListener {
            if (cachedTagData == null) {
                Toast.makeText(this, R.string.toast_read_tag_first, Toast.LENGTH_SHORT).show()
            } else {
                // This opens the system "Save As" screen
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, "my_tag_data.bin")
                }
                createFileLauncher.launch(intent)
            }
        }
        // Update the detectedTag whenever a tag is tapped

        val readFile: Button = findViewById(R.id.btnLoadBin)

        readFile.setOnClickListener {
           val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream" // Looks for binary files
            }
            openFileLauncher.launch(intent)
        }
        
        val readTagButton: Button = findViewById(R.id.btnReadTag)

        readTagButton.setOnClickListener {
           isWriteMode = false
           updateModeIndicator()
           Toast.makeText(this, R.string.toast_ready_to_read, Toast.LENGTH_SHORT).show()
        }


        val writeTagButton: Button = findViewById(R.id.btnWriteTag)

        writeTagButton.setOnClickListener {
            isWriteMode = true
            updateModeIndicator()
            Toast.makeText(this, R.string.toast_ready_to_write, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateModeIndicator() {
        if (isWriteMode) {
            tvModeIndicator.text = getString(R.string.mode_write)
            tvModeIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.mode_write_background))
        } else {
            tvModeIndicator.text = getString(R.string.mode_read)
            tvModeIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.mode_read_background))
        }
    }
}
