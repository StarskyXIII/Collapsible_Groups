package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ItemDataPayloadTest {
	@Test void preservesEveryJsonTypeAndCopiesAtBothBoundaries() {
		for (String literal : List.of("\"1\"", "1", "true", "{}", "[]", "\"\"", "null", "\"null\"")) {
			var data = JsonParser.parseString(literal);
			var payload = new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, data);
			assertEquals(data, ItemDataPayload.parse(payload.toJson()).orElseThrow().data());
		}
		JsonObject data = JsonParser.parseString("{\"value\":1}").getAsJsonObject();
		var payload = new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, data);
		data.addProperty("value", 2);
		payload.data().getAsJsonObject().addProperty("value", 3);
		assertEquals(1, payload.data().getAsJsonObject().get("value").getAsInt());
	}
	@Test void rejectsIncompleteOrAmbiguousPayloads() {
		for (String json : List.of("null", "1", "{}", "{\"data_format\":1,\"data\":1}",
			"{\"data_format\":\"minecraft:nbt\"}", "{\"data_format\":\"minecraft:nbt\",\"data\":1,\"extra\":1}"))
			assertTrue(ItemDataPayload.parse(JsonParser.parseString(json)).isEmpty());
	}
	@Test void explicitPayloadAlwaysDefinesRuntimeAndPersistedValue() {
		var payload = ItemDataPayload.nbt("{id:\"minecraft:stone\",Count:1b}");
		assertEquals(payload.encodedValue(), new GroupFilter.ExactStack("different", payload).encodedStack());
		assertEquals("1b", new GroupFilter.NbtPath("a", "2", ItemDataPayload.nbt("1b")).expectedSnbt());
		assertEquals("{a:1b}", new GroupFilter.Nbt("different", ItemDataPayload.nbt("{a:1b}")).expectedSnbt());
	}
}
