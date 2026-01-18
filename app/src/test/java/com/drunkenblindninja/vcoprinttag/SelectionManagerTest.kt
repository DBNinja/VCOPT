package com.drunkenblindninja.vcoprinttag

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SelectionManagerTest {

    private lateinit var selectionManager: SelectionManager
    private lateinit var options: List<OptionEntry>

    @Before
    fun setup() {
        // Create test options with implies and hints relationships
        options = listOf(
            OptionEntry(
                key = 0,
                name = "food_safe",
                category = "safety",
                display_name = "Food Safe",
                description = "Safe for food contact",
                implies = listOf("non_toxic"),
                hints = listOf("fda_approved")
            ),
            OptionEntry(
                key = 1,
                name = "non_toxic",
                category = "safety",
                display_name = "Non-Toxic",
                description = "Non-toxic material",
                implies = emptyList(),
                hints = emptyList()
            ),
            OptionEntry(
                key = 2,
                name = "fda_approved",
                category = "certifications",
                display_name = "FDA Approved",
                description = "FDA approved for food contact",
                implies = listOf("food_safe"),
                hints = emptyList()
            ),
            OptionEntry(
                key = 3,
                name = "uv_resistant",
                category = "physical",
                display_name = "UV Resistant",
                description = "Resistant to UV light",
                implies = emptyList(),
                hints = listOf("outdoor_use")
            ),
            OptionEntry(
                key = 4,
                name = "outdoor_use",
                category = "applications",
                display_name = "Outdoor Use",
                description = "Suitable for outdoor applications",
                implies = emptyList(),
                hints = emptyList()
            ),
            OptionEntry(
                key = 5,
                name = "esd_safe",
                category = "electrical",
                display_name = "ESD Safe",
                description = "Electrostatic discharge safe",
                implies = emptyList(),
                hints = emptyList()
            )
        )

        selectionManager = SelectionManager(options)
    }

    @Test
    fun test_onOptionSelected_singleOption_addsToSelection() {
        val result = selectionManager.onOptionSelected(5, emptySet())

        assertTrue("Selected option should be in result", result.newSelectedKeys.contains(5))
        assertEquals("Should have exactly 1 selected", 1, result.newSelectedKeys.size)
    }

    @Test
    fun test_onOptionSelected_withImplies_autoSelectsDependencies() {
        // food_safe (0) implies non_toxic (1)
        val result = selectionManager.onOptionSelected(0, emptySet())

        assertTrue("Selected option should be in result", result.newSelectedKeys.contains(0))
        assertTrue("Implied option should be auto-selected", result.newSelectedKeys.contains(1))
        assertEquals("Should have 2 selected (original + implied)", 2, result.newSelectedKeys.size)
    }

    @Test
    fun test_onOptionSelected_withHints_returnsSuggestions() {
        // food_safe (0) hints fda_approved (2)
        val result = selectionManager.onOptionSelected(0, emptySet())

        assertFalse("Hints should NOT be auto-selected", result.newSelectedKeys.contains(2))
        assertEquals("Should have 1 suggestion", 1, result.suggestions.size)
        assertEquals("Suggestion should be fda_approved", "fda_approved", result.suggestions[0].name)
    }

    @Test
    fun test_onOptionSelected_chainedImplies_recursiveAutoSelect() {
        // fda_approved (2) implies food_safe (0) which implies non_toxic (1)
        val result = selectionManager.onOptionSelected(2, emptySet())

        assertTrue("fda_approved should be selected", result.newSelectedKeys.contains(2))
        assertTrue("food_safe should be auto-selected (direct)", result.newSelectedKeys.contains(0))
        assertTrue("non_toxic should be auto-selected (chained)", result.newSelectedKeys.contains(1))
        assertEquals("Should have 3 selected", 3, result.newSelectedKeys.size)
    }

    @Test
    fun test_onOptionSelected_existingSelection_mergesCorrectly() {
        val existing = setOf(5) // esd_safe already selected
        val result = selectionManager.onOptionSelected(3, existing) // Add uv_resistant

        assertTrue("Existing selection should be preserved", result.newSelectedKeys.contains(5))
        assertTrue("New selection should be added", result.newSelectedKeys.contains(3))
        assertEquals("Should have 2 selected", 2, result.newSelectedKeys.size)
    }

    @Test
    fun test_onOptionSelected_hintAlreadySelected_notInSuggestions() {
        // If fda_approved is already selected, it shouldn't appear as a suggestion
        val existing = setOf(2) // fda_approved already selected
        val result = selectionManager.onOptionSelected(0, existing) // Select food_safe

        // food_safe hints fda_approved, but it's already selected
        val suggestionNames = result.suggestions.map { it.name }
        assertFalse("Already selected hint should not be suggested", suggestionNames.contains("fda_approved"))
    }

    @Test
    fun test_onOptionSelected_invalidKey_returnsOriginalSelection() {
        val existing = setOf(1, 2)
        val result = selectionManager.onOptionSelected(999, existing) // Invalid key

        assertEquals("Invalid key should return original selection", existing, result.newSelectedKeys)
        assertTrue("Suggestions should be empty for invalid key", result.suggestions.isEmpty())
    }

    @Test
    fun test_onOptionSelected_noImpliesNoHints_simpleSelect() {
        // esd_safe (5) has no implies or hints
        val result = selectionManager.onOptionSelected(5, emptySet())

        assertEquals("Should only select the option itself", 1, result.newSelectedKeys.size)
        assertTrue("Suggestions should be empty", result.suggestions.isEmpty())
    }

    @Test
    fun test_onOptionSelected_duplicateSelection_noDuplicates() {
        val existing = setOf(0, 1) // food_safe and non_toxic already selected
        val result = selectionManager.onOptionSelected(0, existing) // Re-select food_safe

        // Should not have duplicates
        assertEquals("Should still have 2 items", 2, result.newSelectedKeys.size)
    }

    @Test
    fun test_selectionUpdate_containsCorrectData() {
        val result = selectionManager.onOptionSelected(0, emptySet())

        assertNotNull("Result should not be null", result)
        assertNotNull("newSelectedKeys should not be null", result.newSelectedKeys)
        assertNotNull("suggestions should not be null", result.suggestions)
    }

    @Test
    fun test_optionEntry_dataClassFields() {
        val option = options[0]

        assertEquals(0, option.key)
        assertEquals("food_safe", option.name)
        assertEquals("safety", option.category)
        assertEquals("Food Safe", option.display_name)
        assertEquals("Safe for food contact", option.description)
        assertEquals(1, option.implies.size)
        assertEquals(1, option.hints.size)
    }

    @Test
    fun test_categoryMetadata_structure() {
        val metadata = CategoryMetadata(
            name = "safety",
            display_name = "Safety Properties",
            emoji = "🛡️"
        )

        assertEquals("safety", metadata.name)
        assertEquals("Safety Properties", metadata.display_name)
        assertEquals("🛡️", metadata.emoji)
    }
}
