package org.openprinttag

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.datepicker.MaterialDatePicker
import org.openprinttag.app.databinding.ActivityAuxEditorBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Activity for editing only the aux region fields.
 * Used for partial tag updates that don't modify the main region.
 */
class AuxEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuxEditorBinding
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Date storage
    private var lastStirTime: LocalDate? = null

    // Offset info for partial write (passed from MainActivity)
    private var auxByteOffset: Int = -1

    companion object {
        const val EXTRA_CONSUMED_WEIGHT = "consumed_weight"
        const val EXTRA_WORKGROUP = "workgroup"
        const val EXTRA_USER_DATA = "user_data"
        const val EXTRA_LAST_STIR_TIME = "last_stir_time"
        const val EXTRA_AUX_BYTE_OFFSET = "aux_byte_offset"

        const val RESULT_CONSUMED_WEIGHT = "result_consumed_weight"
        const val RESULT_WORKGROUP = "result_workgroup"
        const val RESULT_USER_DATA = "result_user_data"
        const val RESULT_LAST_STIR_TIME = "result_last_stir_time"
        const val RESULT_AUX_OFFSET = "result_aux_offset"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuxEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle system insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Setup date picker
        setupDatePicker()

        // Pre-fill from intent extras
        preFillFromIntent()

        // Setup update button
        binding.btnUpdateAux.setOnClickListener {
            returnAuxData()
        }
    }

    private fun setupDatePicker() {
        binding.getLastStirTime.setOnClickListener {
            showDatePicker(lastStirTime) { date ->
                lastStirTime = date
                binding.getLastStirTime.setText(date.format(dateFormatter))
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

    private fun preFillFromIntent() {
        // Get offset info
        auxByteOffset = intent.getIntExtra(EXTRA_AUX_BYTE_OFFSET, -1)

        // Pre-fill fields
        intent.getFloatExtra(EXTRA_CONSUMED_WEIGHT, Float.MIN_VALUE).let {
            if (it != Float.MIN_VALUE) {
                binding.getConsumedWeight.setText(it.toString())
            }
        }

        intent.getStringExtra(EXTRA_WORKGROUP)?.let {
            binding.getWorkgroup.setText(it)
        }

        intent.getStringExtra(EXTRA_USER_DATA)?.let {
            binding.getUserData.setText(it)
        }

        intent.getLongExtra(EXTRA_LAST_STIR_TIME, Long.MIN_VALUE).let {
            if (it != Long.MIN_VALUE) {
                lastStirTime = LocalDate.ofEpochDay(it)
                binding.getLastStirTime.setText(lastStirTime?.format(dateFormatter))
            }
        }
    }

    private fun returnAuxData() {
        val resultIntent = Intent().apply {
            // Pass consumed weight
            binding.getConsumedWeight.text?.toString()?.toFloatOrNull()?.let {
                putExtra(RESULT_CONSUMED_WEIGHT, it)
            }

            // Pass workgroup
            binding.getWorkgroup.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                putExtra(RESULT_WORKGROUP, it)
            }

            // Pass user data
            binding.getUserData.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                putExtra(RESULT_USER_DATA, it)
            }

            // Pass last stir time as epoch day
            lastStirTime?.let {
                putExtra(RESULT_LAST_STIR_TIME, it.toEpochDay())
            }

            // Pass offset for partial write
            putExtra(RESULT_AUX_OFFSET, auxByteOffset)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
