package org.vcoprinttag

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.vcoprinttag.R
import org.vcoprinttag.databinding.ActivityCreditsBinding
import org.vcoprinttag.model.LibraryLicense
import org.vcoprinttag.model.LicenseRepository

class CreditsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreditsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreditsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        setupContributors()
        setupLicensesList()
    }

    private fun setupContributors() {
        binding.githubLinkDbninja.setOnClickListener {
            openUrl(getString(R.string.github_dbninja))
        }

        binding.githubLinkUnderflow.setOnClickListener {
            openUrl(getString(R.string.github_underflow))
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun setupLicensesList() {
        val licenses = LicenseRepository.getLicenses(this)
        val container = binding.licensesContainer

        licenses.forEach { license ->
            val itemView = createLicenseItemView(license)
            container.addView(itemView)
        }
    }

    private fun createLicenseItemView(license: LibraryLicense): LinearLayout {
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            }
            setBackgroundResource(android.R.attr.selectableItemBackground.let { attr ->
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(attr, outValue, true)
                outValue.resourceId
            })
            setPadding(
                resources.getDimensionPixelSize(R.dimen.spacing_sm),
                resources.getDimensionPixelSize(R.dimen.spacing_md),
                resources.getDimensionPixelSize(R.dimen.spacing_sm),
                resources.getDimensionPixelSize(R.dimen.spacing_md)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { showLicenseDialog(license) }
        }

        val nameTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            text = license.libraryName
            setTextAppearance(R.style.TextAppearance_OpenPrintTag_BodyLarge)
            setTextColor(getColor(android.R.color.transparent).let {
                val outValue = android.util.TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, outValue, true)
                getColor(outValue.resourceId)
            })
        }

        val chip = Chip(this).apply {
            text = license.licenseType
            isClickable = false
            setTextAppearance(R.style.TextAppearance_OpenPrintTag_BodySmall)
        }

        itemLayout.addView(nameTextView)
        itemLayout.addView(chip)

        return itemLayout
    }

    private fun showLicenseDialog(license: LibraryLicense) {
        MaterialAlertDialogBuilder(this)
            .setTitle(license.libraryName)
            .setMessage("${license.copyright}\n\n${license.licenseFullText}")
            .setPositiveButton(R.string.dialog_btn_done, null)
            .show()
    }
}
