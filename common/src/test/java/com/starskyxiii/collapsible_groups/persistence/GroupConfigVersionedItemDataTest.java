package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.internal.version.data.MinecraftItemDataFormats;
import com.starskyxiii.collapsible_groups.internal.version.data.VersionedDataEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GroupConfigVersionedItemDataTest {
	@Test
	void currentEnvelopeRemainsByteForByteEquivalentThroughFilterRoundTrip() {
		String envelope = VersionedDataEnvelope.wrap(
			MinecraftItemDataFormats.EXACT_STACK_1_20_1,
			new com.google.gson.JsonPrimitive("{id:\"minecraft:stone\",Count:1b}"));
		JsonObject source = exactNode(envelope);

		GroupFilter.ExactStack parsed = assertInstanceOf(GroupFilter.ExactStack.class, GroupConfig.parseFilter(source));
		assertEquals(envelope, parsed.encodedStack());
		assertEquals(source, GroupConfig.serializeFilter(parsed));
	}

	@Test
	void foreignAndMalformedEnvelopesPreserveTheWholeNodeAsUnsupported() {
		String foreign = "{\"$collapsible_groups\":{" +
			"\"schema\":\"collapsible_groups:exact_stack\"," +
			"\"schema_version\":1," +
			"\"data_format\":\"minecraft:item_nbt\"," +
			"\"source_minecraft\":\"1.20.1\"," +
			"\"data\":\"opaque\"}}";
		String malformed = "{\"$collapsible_groups\":{\"schema\":\"collapsible_groups:exact_stack\"}}";

		assertUnsupportedRoundTrip(exactNode(foreign));
		assertUnsupportedRoundTrip(exactNode(malformed));
	}

	@Test
	void legacyComponentNodeIsPreservedAsUnsupported() {
		String value = "{\"schema\":\"user-data\",\"data_format\":\"custom\",\"source_minecraft\":\"label\"}";
		JsonObject source = JsonParser.parseString("{" +
			"\"type\":\"item\"," +
			"\"component\":\"minecraft:custom_data\"," +
			"\"value\":" + quote(value) + "}").getAsJsonObject();

		assertUnsupportedRoundTrip(source);
	}

	@Test
	void componentEnvelopesAreOpaqueOn1201() {
		String current = VersionedDataEnvelope.wrap(
			MinecraftItemDataFormats.COMPONENT_VALUE_1_21_1,
			JsonParser.parseString("{\"value\":1}"));
		JsonObject currentNode = componentNode(current);
		assertUnsupportedRoundTrip(currentNode);

		String foreign = "{\"$collapsible_groups\":{" +
			"\"schema\":\"collapsible_groups:component_value\"," +
			"\"schema_version\":1," +
			"\"data_format\":\"minecraft:item_nbt\"," +
			"\"source_minecraft\":\"1.20.1\"," +
			"\"data\":\"opaque\"}}";
		assertUnsupportedRoundTrip(componentNode(foreign));
	}

	@Test
	void unversioned121ExactPayloadIsPreservedAsUnsupported() {
		assertUnsupportedRoundTrip(exactNode("{\"id\":\"minecraft:stone\"}"));
	}

	private static void assertUnsupportedRoundTrip(JsonObject source) {
		GroupFilter.Unsupported unsupported = assertInstanceOf(
			GroupFilter.Unsupported.class, GroupConfig.parseFilter(source));
		assertEquals(source, unsupported.rawJson());
		assertEquals(source, GroupConfig.serializeFilter(unsupported));
	}

	private static JsonObject exactNode(String encoded) {
		JsonObject node = new JsonObject();
		node.addProperty("type", "item");
		node.addProperty("stack", encoded);
		return node;
	}

	private static JsonObject componentNode(String encoded) {
		JsonObject node = new JsonObject();
		node.addProperty("type", "item");
		node.addProperty("component", "minecraft:custom_data");
		node.addProperty("value", encoded);
		return node;
	}

	private static String quote(String value) {
		return new com.google.gson.Gson().toJson(value);
	}
}
