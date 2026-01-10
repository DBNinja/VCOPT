package org.vcoprinttag.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.vcoprinttag.R

/**
 * RecyclerView adapter for displaying tag data in a card-based layout.
 * Supports multiple ViewHolder types for different display sections.
 */
class TagDataAdapter(
    private var items: List<TagDisplayItem> = emptyList()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_MATERIAL_HEADER = 0
        private const val TYPE_SECTION_HEADER = 1
        private const val TYPE_PROPERTY_ROW = 2
        private const val TYPE_TEMPERATURE_ROW = 3
        private const val TYPE_USAGE_PROGRESS = 4
        private const val TYPE_COLOR_SWATCHES = 5
        private const val TYPE_CHIP_GROUP = 6
        private const val TYPE_EMPTY_STATE = 7
    }

    fun updateItems(newItems: List<TagDisplayItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TagDisplayItem.MaterialHeader -> TYPE_MATERIAL_HEADER
            is TagDisplayItem.SectionHeader -> TYPE_SECTION_HEADER
            is TagDisplayItem.PropertyRow -> TYPE_PROPERTY_ROW
            is TagDisplayItem.TemperatureRow -> TYPE_TEMPERATURE_ROW
            is TagDisplayItem.UsageProgress -> TYPE_USAGE_PROGRESS
            is TagDisplayItem.ColorSwatches -> TYPE_COLOR_SWATCHES
            is TagDisplayItem.ChipGroup -> TYPE_CHIP_GROUP
            is TagDisplayItem.EmptyState -> TYPE_EMPTY_STATE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_MATERIAL_HEADER -> MaterialHeaderViewHolder(
                inflater.inflate(R.layout.item_material_header, parent, false)
            )
            TYPE_SECTION_HEADER -> SectionHeaderViewHolder(
                inflater.inflate(R.layout.item_section_header, parent, false)
            )
            TYPE_PROPERTY_ROW -> PropertyRowViewHolder(
                inflater.inflate(R.layout.item_property_row, parent, false)
            )
            TYPE_TEMPERATURE_ROW -> TemperatureRowViewHolder(
                inflater.inflate(R.layout.item_temperature_row, parent, false)
            )
            TYPE_USAGE_PROGRESS -> UsageProgressViewHolder(
                inflater.inflate(R.layout.item_usage_progress, parent, false)
            )
            TYPE_COLOR_SWATCHES -> ColorSwatchesViewHolder(
                inflater.inflate(R.layout.item_color_swatches, parent, false)
            )
            TYPE_CHIP_GROUP -> ChipGroupViewHolder(
                inflater.inflate(R.layout.item_chip_group, parent, false)
            )
            TYPE_EMPTY_STATE -> EmptyStateViewHolder(
                inflater.inflate(R.layout.item_empty_state, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TagDisplayItem.MaterialHeader -> (holder as MaterialHeaderViewHolder).bind(item)
            is TagDisplayItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item)
            is TagDisplayItem.PropertyRow -> (holder as PropertyRowViewHolder).bind(item)
            is TagDisplayItem.TemperatureRow -> (holder as TemperatureRowViewHolder).bind(item)
            is TagDisplayItem.UsageProgress -> (holder as UsageProgressViewHolder).bind(item)
            is TagDisplayItem.ColorSwatches -> (holder as ColorSwatchesViewHolder).bind(item)
            is TagDisplayItem.ChipGroup -> (holder as ChipGroupViewHolder).bind(item)
            is TagDisplayItem.EmptyState -> (holder as EmptyStateViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    // ========== ViewHolders ==========

    class MaterialHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val brandName: TextView = itemView.findViewById(R.id.tvBrandName)
        private val materialName: TextView = itemView.findViewById(R.id.tvMaterialName)
        private val materialInfo: TextView = itemView.findViewById(R.id.tvMaterialInfo)
        private val colorContainer: LinearLayout = itemView.findViewById(R.id.colorContainer)

        fun bind(item: TagDisplayItem.MaterialHeader) {
            // Brand and Material Name
            val displayName = buildString {
                item.brandName?.let { append(it) }
                if (item.brandName != null && item.materialName != null) append(" ")
                item.materialName?.let { append(it) }
            }
            brandName.text = displayName.ifEmpty { "Unknown Material" }
            materialName.visibility = View.GONE // Combined into brandName

            // Material Class and Type
            val infoText = buildString {
                append(item.materialClass)
                item.materialType?.let {
                    append(" • ")
                    append(it)
                }
            }
            materialInfo.text = infoText

            // Color swatches
            colorContainer.removeAllViews()
            val allColors = listOfNotNull(item.primaryColor) + item.secondaryColors

            for (colorHex in allColors.take(6)) {
                val swatch = createColorSwatch(colorHex)
                colorContainer.addView(swatch)
            }

            colorContainer.visibility = if (allColors.isEmpty()) View.GONE else View.VISIBLE
        }

        private fun createColorSwatch(colorHex: String): View {
            val context = itemView.context
            val size = context.resources.getDimensionPixelSize(R.dimen.color_swatch_size)
            val margin = context.resources.getDimensionPixelSize(R.dimen.spacing_xs)

            val view = View(context)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = margin
            view.layoutParams = params

            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.OVAL
            try {
                drawable.setColor(parseColor(colorHex))
            } catch (e: Exception) {
                drawable.setColor(Color.GRAY)
            }
            drawable.setStroke(
                2,
                ContextCompat.getColor(context, R.color.outline)
            )
            view.background = drawable

            return view
        }
    }

    class SectionHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvSectionTitle)

        fun bind(item: TagDisplayItem.SectionHeader) {
            title.text = item.title.uppercase()
        }
    }

    class PropertyRowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.tvPropertyLabel)
        private val value: TextView = itemView.findViewById(R.id.tvPropertyValue)
        private val secondaryValue: TextView = itemView.findViewById(R.id.tvSecondaryValue)

        fun bind(item: TagDisplayItem.PropertyRow) {
            label.text = item.label
            value.text = item.value

            if (item.secondaryValue != null) {
                secondaryValue.visibility = View.VISIBLE
                secondaryValue.text = item.secondaryValue
            } else {
                secondaryValue.visibility = View.GONE
            }
        }
    }

    class TemperatureRowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.tvTempLabel)
        private val value: TextView = itemView.findViewById(R.id.tvTempValue)

        fun bind(item: TagDisplayItem.TemperatureRow) {
            label.text = item.label

            value.text = when {
                item.singleValue != null -> "${item.singleValue}°C"
                item.minTemp != null && item.maxTemp != null -> "${item.minTemp}-${item.maxTemp}°C"
                item.minTemp != null -> "${item.minTemp}°C+"
                item.maxTemp != null -> "≤${item.maxTemp}°C"
                else -> "N/A"
            }
        }
    }

    class UsageProgressViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressUsage)
        private val progressText: TextView = itemView.findViewById(R.id.tvProgressText)
        private val detailText: TextView = itemView.findViewById(R.id.tvUsageDetail)

        fun bind(item: TagDisplayItem.UsageProgress) {
            val total = item.totalWeight ?: 1000f
            val consumed = item.consumedWeight ?: 0f
            val remaining = (total - consumed).coerceAtLeast(0f)
            val percentage = ((remaining / total) * 100).toInt().coerceIn(0, 100)

            progressBar.max = 100
            progressBar.progress = percentage

            progressText.text = "$percentage% remaining"

            val detailStr = buildString {
                append("${remaining.toInt()}g remaining")
                if (consumed > 0) {
                    append(" • ${consumed.toInt()}g used")
                }
                item.workgroup?.takeIf { it.isNotBlank() }?.let {
                    append(" • $it")
                }
            }
            detailText.text = detailStr
        }
    }

    class ColorSwatchesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.swatchContainer)

        fun bind(item: TagDisplayItem.ColorSwatches) {
            container.removeAllViews()

            for (colorHex in item.colors) {
                val context = itemView.context
                val size = context.resources.getDimensionPixelSize(R.dimen.color_swatch_size_large)
                val margin = context.resources.getDimensionPixelSize(R.dimen.spacing_sm)

                val view = View(context)
                val params = LinearLayout.LayoutParams(size, size)
                params.marginEnd = margin
                view.layoutParams = params

                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.OVAL
                try {
                    drawable.setColor(parseColor(colorHex))
                } catch (e: Exception) {
                    drawable.setColor(Color.GRAY)
                }
                drawable.setStroke(
                    2,
                    ContextCompat.getColor(context, R.color.outline)
                )
                view.background = drawable
                container.addView(view)
            }
        }
    }

    class ChipGroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val chipGroup: ChipGroup = itemView.findViewById(R.id.chipGroup)

        fun bind(item: TagDisplayItem.ChipGroup) {
            chipGroup.removeAllViews()

            for (text in item.items) {
                val chip = Chip(itemView.context)
                chip.text = text
                chip.isCheckable = false
                chip.isClickable = false

                if (item.isOutlined) {
                    chip.setChipBackgroundColorResource(android.R.color.transparent)
                    chip.setChipStrokeColorResource(R.color.outline)
                    chip.chipStrokeWidth = itemView.context.resources.getDimension(R.dimen.divider_thickness)
                } else {
                    chip.setChipBackgroundColorResource(R.color.primary_container)
                    chip.setTextColor(ContextCompat.getColor(itemView.context, R.color.on_primary_container))
                }

                chipGroup.addView(chip)
            }
        }
    }

    class EmptyStateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val message: TextView = itemView.findViewById(R.id.tvEmptyMessage)

        fun bind(item: TagDisplayItem.EmptyState) {
            message.text = item.message
        }
    }
}

/**
 * Parse color from various formats (hex with/without #, RGB, ARGB)
 */
private fun parseColor(colorStr: String): Int {
    val cleaned = colorStr.trim().removePrefix("#")
    return when (cleaned.length) {
        6 -> Color.parseColor("#$cleaned")
        8 -> Color.parseColor("#$cleaned")
        3 -> {
            // Shorthand like "F00" -> "FF0000"
            val expanded = cleaned.map { "$it$it" }.joinToString("")
            Color.parseColor("#$expanded")
        }
        else -> Color.GRAY
    }
}
