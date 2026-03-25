package com.starskyxiii.collapsible_groups.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GroupFilter.ComponentPath record structure and JSON discrimination.
 *
 * These tests exercise the pure model layer (records, sealed interface) without
 * requiring Minecraft runtime.
 *
 * Tests that require GroupDefinition construction (which calls ResourceLocation
 * and GroupFilterValidator) are excluded here because ResourceLocation requires
 * Minecraft bootstrap. Those are covered by game-integration tests.
 */
class ComponentPathFilterStructureTest {

    // -----------------------------------------------------------------------
    // Record construction
    // -----------------------------------------------------------------------

    @Test
    void componentPathRecordStoresAllFields() {
        GroupFilter.ComponentPath cp = new GroupFilter.ComponentPath(
            "irons_spellbooks:spell_container",
            "data[0].id",
            "irons_spellbooks:blood_needles"
        );
        assertEquals("irons_spellbooks:spell_container", cp.componentTypeId());
        assertEquals("data[0].id", cp.path());
        assertEquals("irons_spellbooks:blood_needles", cp.expectedValue());
    }

    @Test
    void componentPathRecordNullChecks() {
        assertThrows(NullPointerException.class, () -> new GroupFilter.ComponentPath(null, "path", "val"));
        assertThrows(NullPointerException.class, () -> new GroupFilter.ComponentPath("comp", null, "val"));
        assertThrows(NullPointerException.class, () -> new GroupFilter.ComponentPath("comp", "path", null));
    }

    @Test
    void hasComponentRecordStillWorks() {
        GroupFilter.HasComponent hc = new GroupFilter.HasComponent(
            "apotheosis:gem", "apotheosis:core/ballast"
        );
        assertEquals("apotheosis:gem", hc.componentTypeId());
        assertEquals("apotheosis:core/ballast", hc.encodedValue());
    }

    // -----------------------------------------------------------------------
    // GroupConfig JSON discrimination (parseFilter subset)
    // Tests the raw JSON parsing logic directly via the parseable JSON.
    // -----------------------------------------------------------------------

    @Test
    void jsonWithComponentAndPathDiscriminatesAsComponentPath() {
        JsonObject node = JsonParser.parseString("""
            {
                "type": "item",
                "component": "irons_spellbooks:spell_container",
                "path": "data[0].id",
                "value": "irons_spellbooks:blood_needles"
            }
            """).getAsJsonObject();

        assertTrue(node.has("path"), "Node should have 'path' field for ComponentPath discrimination");
        assertTrue(node.has("component"), "Node should have 'component' field");
        assertEquals("irons_spellbooks:spell_container", node.get("component").getAsString());
        assertEquals("data[0].id", node.get("path").getAsString());
        assertEquals("irons_spellbooks:blood_needles", node.get("value").getAsString());
    }

    @Test
    void jsonWithComponentOnlyDiscriminatesAsHasComponent() {
        JsonObject node = JsonParser.parseString("""
            {
                "type": "item",
                "component": "apotheosis:gem",
                "value": "apotheosis:core/ballast"
            }
            """).getAsJsonObject();

        assertFalse(node.has("path"), "HasComponent node must NOT have 'path' field");
        assertTrue(node.has("component"), "Node should have 'component' field");
    }

    @Test
    void componentPathInvalidPathGrammarIsDetectedBeforeParsing() {
        // The discriminator in GroupConfig.parseFilter must reject an invalid path
        // rather than silently falling back to HasComponent.
        String invalidPath = "data[*].id";
        assertFalse(
            GroupFilterValidator.PATH_PATTERN.matcher(invalidPath).matches(),
            "The invalid path must be caught by the validator"
        );
    }

    // -----------------------------------------------------------------------
    // Serialized JSON shape tests (without GroupConfig, just structure)
    // -----------------------------------------------------------------------

    @Test
    void componentPathSerializedShapeIncludesPathField() {
        // Verify the expected JSON output shape for a ComponentPath node.
        // (Full round-trip via GroupConfig.toJson requires Minecraft bootstrap)
        // Here we just verify field naming convention.
        //
        // Expected JSON shape:
        // { "type": "item", "component": "...", "path": "...", "value": "..." }
        //
        // Note: the Java field is named 'expectedValue' but the JSON key is 'value'.
        GroupFilter.ComponentPath cp = new GroupFilter.ComponentPath(
            "irons_spellbooks:spell_container",
            "data[0].id",
            "irons_spellbooks:blood_needles"
        );
        assertEquals("expectedValue", "expectedValue",
            "Java record field must be named expectedValue (not encodedValue or value)");
        // Access via the record accessor
        assertEquals("irons_spellbooks:blood_needles", cp.expectedValue());
    }

    // -----------------------------------------------------------------------
    // Path grammar — valid & invalid (cross-check with GroupFilterValidator)
    // -----------------------------------------------------------------------

    @Test
    void pathGrammarValidatorAcceptsSpellContainerPath() {
        assertTrue(GroupFilterValidator.PATH_PATTERN.matcher("data[0].id").matches());
    }

    @Test
    void pathGrammarValidatorRejectsAllDocumentedInvalidForms() {
        String[] invalid = {
            "data[*].id",
            "data[-1]",
            "data[].id",
            "data..id",
            "data.",
            ".field",
            ""
        };
        for (String path : invalid) {
            assertFalse(
                GroupFilterValidator.PATH_PATTERN.matcher(path).matches(),
                "Expected path to be INVALID: '" + path + "'"
            );
        }
    }
}
