package org.openprinttag

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.openprinttag.model.OpenPrintTagModel
import org.openprinttag.model.Serializer
import java.io.File
import java.io.FileOutputStream

class GeneratorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generator)

        val etBrand = findViewById<EditText>(R.id.etBrand)
        val etMaterialName = findViewById<EditText>(R.id.etMaterialName)
        val etColor = findViewById<EditText>(R.id.etColor)
        val etGtin = findViewById<EditText>(R.id.etGtin)
        val etMin = findViewById<EditText>(R.id.etMinTemp)
        val etMax = findViewById<EditText>(R.id.etMaxTemp)

        val btnGenerate = findViewById<Button>(R.id.btnGenerateBin)
        btnGenerate.setOnClickListener {
            val model = OpenPrintTagModel(
                brand = etBrand.text.toString(),
                materialName = etMaterialName.text.toString(),
                primaryColor = etColor.text.toString(),
                gtin = etGtin.text.toString().ifBlank { null },
                minPrintTemp = etMin.text.toString().toIntOrNull(),
                maxPrintTemp = etMax.text.toString().toIntOrNull()
            )
            val bin = Serializer.serialize(model)
            val f = File(filesDir, "openprinttag.bin")
            FileOutputStream(f).use { it.write(bin) }
            Toast.makeText(this, "Generated ${bin.size} bytes -> ${f.absolutePath}", Toast.LENGTH_LONG).show()
            val out = Intent()
            out.putExtra("bin_path", f.absolutePath)
            setResult(Activity.RESULT_OK, out)
            finish()
        }
    }
}
