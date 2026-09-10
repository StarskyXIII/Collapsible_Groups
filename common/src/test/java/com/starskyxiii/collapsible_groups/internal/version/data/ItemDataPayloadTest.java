package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ItemDataPayloadTest {
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "01", "+1", ".1", "TRUE", "NaN", "undefined", "{unquoted:1}", "1 2", "'x'", "\"unfinished", "]", "{} trailing", "/*x*/1", "[1,]", "{\"x\":1,}"})
    void literalParserRejectsIncompleteAndLenientJson(String value) {
        assertThrows(IllegalArgumentException.class, () -> ItemDataPayload.parseLiteral(value), value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "-1.5e2", "\"1\"", "true", "false", "null", "\"null\"", "\"\"", "[]", "{}", " [1,\"1\",null] "})
    void completeJsonValuesRetainTheirTypes(String value) {
        assertEquals(JsonParser.parseString(value), ItemDataPayload.parseLiteral(value));
    }

    @Test void payloadDefensivelyCopiesObjectsAndKeepsExplicitNull() {
        JsonObject source = new JsonObject();
        source.add("nil", JsonNull.INSTANCE);
        ItemDataPayload payload = new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, source);
        source.addProperty("changed", true);
        payload.data().getAsJsonObject().addProperty("mutated", true);
        assertEquals("{\"nil\":null}", payload.data().toString());
        assertTrue(payload.toJson().getAsJsonObject("data").has("nil"));
        assertThrows(IllegalArgumentException.class, () -> new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, new JsonPrimitive(Float.NaN)));
    }

    @Test void legacyStringMatchingAndTypedJsonMatchingAreSeparate() {
        JsonPrimitive string = new JsonPrimitive("1");
        JsonPrimitive number = new JsonPrimitive(1);
        assertTrue(Minecraft121ItemDataAccess.matchesEncodedValue(string, "1"));
        assertTrue(Minecraft121ItemDataAccess.matchesEncodedValue(number, "1"));
        assertTrue(Minecraft121ItemDataAccess.matchesEncodedValue(string, new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, string)));
        assertFalse(Minecraft121ItemDataAccess.matchesEncodedValue(number, new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, string)));
        assertTrue(Minecraft121ItemDataAccess.matchesEncodedValue(number, new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, number)));
        assertFalse(Minecraft121ItemDataAccess.matchesEncodedValue(string, new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, number)));
        assertFalse(Minecraft121ItemDataAccess.matchesEncodedValue(JsonNull.INSTANCE, new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, new JsonPrimitive("null"))));
    }
}
