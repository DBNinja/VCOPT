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
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import org.openprinttag.app.R   // use the actual namespace where R was generated
import android.widget.Toast
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
    private var loadedBin: ByteArray? = null
    private var lastReadBin: ByteArray? = null
    private var btnLaunchGenerator: Button? = null
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
                    tvStatus.text = "Status: New bin generated and cached"
                } ?: run {
                    tvTagInfo.text = "Status: No data cached"
                    tvStatus.text = "Status: No data cached"
                }
                //tvTagInfo.text = cachedTagData.toHexString()
                //tvStatus.text = "Status: New bin generated and cached"
                Toast.makeText(this, "Data ready to write!", Toast.LENGTH_SHORT).show()
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

                        Toast.makeText(this@MainActivity, "File loaded to memory!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("NFC_APP", "Failed to load file", e)
            }
        }
    }

    private fun triggerFileSave() {
        if (detectedTag == null) {
            Toast.makeText(this, "No tag detected! Please tap a tag first.", Toast.LENGTH_SHORT).show()
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

        lifecycleScope.launch(Dispatchers.IO) {
            val data = manager.readFullTag()

            withContext(Dispatchers.Main) {
                if (data != null) {
                    cachedTagData = data // Save to memory
                    statusText.text = "Tag Read Successfully (${data.size} bytes)"

                    // Convert to Hex and display
                    dataDisplay.text = data.toHexString()
                } else {
                    statusText.text = "Read Failed. Keep tag closer."
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
                    Toast.makeText(this@MainActivity, "Saved from memory!", Toast.LENGTH_SHORT).show()
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
                if (isWriteMode) {
                    // EXECUTE WRITE
                    lifecycleScope.launch {
                        val success = writeMemoryToTag(it)
                        if (success) {
                            Toast.makeText(this@MainActivity, "Write Complete!", Toast.LENGTH_SHORT).show()
                            isWriteMode = false // Turn off write mode after success
                            // updateUi()
                        } else {
                            Toast.makeText(this@MainActivity, "Write Failed. Try again.", Toast.LENGTH_SHORT).show()
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
        //setContentView(R.layout.activity_main)
        // INITIALIZE ALL VIEWS FIRST
        tvStatus = findViewById(R.id.tvStatus)
        tvTagInfo = findViewById(R.id.tvTagInfo)
        val btnLaunchGenerator = requireNotNull(
            findViewById<Button>(R.id.btnLaunchGenerator)
        ) { "btnLaunchGenerator not found in activity_main.xml" }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        //these two are in onCreate

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
                Toast.makeText(this, "Read a tag first!", Toast.LENGTH_SHORT).show()
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
           Toast.makeText(this, "Ready to Read Tag", Toast.LENGTH_SHORT).show()
        }


        val writeTagButton: Button = findViewById(R.id.btnWriteTag)

            writeTagButton.setOnClickListener {
            isWriteMode = true
            Toast.makeText(this, "Ready to Write to Tag", Toast.LENGTH_SHORT).show()
        }


        fun onNewIntent(intent: Intent) {
            super.onNewIntent(intent)
            if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
                NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

                detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                Toast.makeText(this, "Tag Ready to Read", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
