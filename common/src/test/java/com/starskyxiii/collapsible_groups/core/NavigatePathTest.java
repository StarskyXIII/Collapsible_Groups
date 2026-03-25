package com.starskyxiii.collapsible_groups.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ComponentPathNavigator#navigatePath}.
 *
 * These tests are pure Java + Gson — no Minecraft runtime required.
 *
 * Boundary rules:
 *   - Returns null when navigation fails (missing field, wrong type, out-of-range)
 *   - Returns JsonNull when the selected element is JSON null
 *   - The path must already be grammar-valid when this method is called
 */
class NavigatePathTest {

    private static JsonElement json(String raw) {
        return JsonParser.parseString(raw);
    }

    private static JsonElement navigate(JsonElement root, String path) {
        return ComponentPathNavigator.navigatePath(root, path);
    }

    // -----------------------------------------------------------------------
    // Object field navigation
    // -----------------------------------------------------------------------

    @Test
    void singleField() {
        JsonElement root = json("{\"id\": \"irons_spellbooks:blood_needles\"}");
        JsonElement result = navigate(root, "id");
        assertNotNull(result);
        assertEquals("irons_spellbooks:blood_needles", result.getAsString());
    }

    @Test
    void nestedField() {
        JsonElement root = json("{\"meta\": {\"type\": \"spell\"}}");
        JsonElement result = navigate(root, "meta.type");
        assertNotNull(result);
        assertEquals("spell", result.getAsString());
    }

    @Test
    void threeNested() {
        JsonElement root = json("{\"a\": {\"b\": {\"c\": 42}}}");
        JsonElement result = navigate(root, "a.b.c");
        assertNotNull(result);
        assertEquals(42, result.getAsInt());
    }

    // -----------------------------------------------------------------------
    // Array index navigation
    // -----------------------------------------------------------------------

    @Test
    void arrayIndex() {
        JsonElement root = json("{\"data\": [\"first\", \"second\"]}");
        JsonElement result = navigate(root, "data[0]");
        assertNotNull(result);
        assertEquals("first", result.getAsString());
    }

    @Test
    void arrayIndexNonZero() {
        JsonElement root = json("{\"data\": [\"first\", \"second\", \"third\"]}");
        JsonElement result = navigate(root, "data[2]");
        assertNotNull(result);
        assertEquals("third", result.getAsString());
    }

    @Test
    void arrayIndexThenField() {
        JsonElement root = json("{\"data\": [{\"id\": \"irons_spellbooks:blood_needles\", \"level\": 1}]}");
        JsonElement result = navigate(root, "data[0].id");
        assertNotNull(result);
        assertEquals("irons_spellbooks:blood_needles", result.getAsString());
    }

    @Test
    void deepArrayPlusNestedField() {
        JsonElement root = json("{\"items\": [{\"meta\": {\"name\": \"sword\"}}, {\"meta\": {\"name\": \"shield\"}}]}");
        JsonElement result = navigate(root, "items[1].meta.name");
        assertNotNull(result);
        assertEquals("shield", result.getAsString());
    }

    // -----------------------------------------------------------------------
    // Failure cases — must return null
    // -----------------------------------------------------------------------

    @Test
    void missingFieldReturnsNull() {
        JsonElement root = json("{\"a\": 1}");
        assertNull(navigate(root, "missing"));
    }

    @Test
    void wrongTypeForObjectAccessReturnsNull() {
        // root is an array, but we try to access an object field
        JsonElement root = json("[1, 2, 3]");
        assertNull(navigate(root, "field"));
    }

    @Test
    void wrongTypeForArrayAccessReturnsNull() {
        // field is a string, but we try to use an array index on it
        JsonElement root = json("{\"data\": \"not-an-array\"}");
        assertNull(navigate(root, "data[0]"));
    }

    @Test
    void outOfRangeIndexReturnsNull() {
        JsonElement root = json("{\"data\": [\"only\"]}");
        assertNull(navigate(root, "data[5]"));
    }

    @Test
    void missingNestedFieldReturnsNull() {
        JsonElement root = json("{\"meta\": {\"type\": \"spell\"}}");
        assertNull(navigate(root, "meta.missing"));
    }

    // -----------------------------------------------------------------------
    // JsonNull handling — must return JsonNull (not null)
    // -----------------------------------------------------------------------

    @Test
    void selectedJsonNullReturnsJsonNullInstance() {
        JsonElement root = json("{\"value\": null}");
        JsonElement result = navigate(root, "value");
        // Navigation succeeded but the value is JSON null
        assertNotNull(result, "navigatePath should return JsonNull instance, not Java null");
        assertSame(JsonNull.INSTANCE, result);
    }

    @Test
    void selectedJsonNullInArrayReturnsJsonNullInstance() {
        JsonElement root = json("{\"data\": [null, \"second\"]}");
        JsonElement result = navigate(root, "data[0]");
        assertNotNull(result);
        assertSame(JsonNull.INSTANCE, result);
    }
}
