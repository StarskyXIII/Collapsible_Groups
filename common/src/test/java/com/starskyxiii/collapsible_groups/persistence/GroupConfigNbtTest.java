package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.internal.version.data.MinecraftItemDataFormats;
import com.starskyxiii.collapsible_groups.internal.version.data.VersionedDataEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GroupConfigNbtTest {
	@Test
	void nativeRootAndPathUseDistinctCurrentEnvelopes() {
		JsonObject rootJson = GroupConfig.serializeFilter(new GroupFilter.Nbt("{value:1b}"));
		JsonObject pathJson = GroupConfig.serializeFilter(new GroupFilter.NbtPath("list[0]", "1b"));

		assertEquals("item", rootJson.get("type").getAsString());
		assertEquals("item", pathJson.get("type").getAsString());
		assertEquals(VersionedDataEnvelope.Support.CURRENT,
			VersionedDataEnvelope.inspect(rootJson.get("nbt").getAsString(), MinecraftItemDataFormats.NBT_VALUE_1_20_1).support());
		assertEquals(VersionedDataEnvelope.Support.CURRENT,
			VersionedDataEnvelope.inspect(pathJson.get("value").getAsString(), MinecraftItemDataFormats.NBT_VALUE_1_20_1).support());
		assertInstanceOf(GroupFilter.Nbt.class, GroupConfig.parseFilter(rootJson));
		GroupFilter.NbtPath path = assertInstanceOf(GroupFilter.NbtPath.class, GroupConfig.parseFilter(pathJson));
		assertEquals("list[0]", path.path());
		assertEquals("1b", path.expectedSnbt());
	}

	@Test
	void rawSnbtUnsupportedEnvelopeAndInvalidNativeValuesRemainOpaque() {
		String foreign = VersionedDataEnvelope.wrap(
			MinecraftItemDataFormats.COMPONENT_VALUE_1_21_1, new JsonPrimitive("{value:1b}"));
		String wrongDataShape = VersionedDataEnvelope.wrap(
			MinecraftItemDataFormats.NBT_VALUE_1_20_1, new JsonPrimitive(3));
		for (JsonObject source : java.util.List.of(
			JsonParser.parseString("{\"type\":\"item\",\"nbt\":\"{value:1b}\"}").getAsJsonObject(),
			object("nbt", foreign),
			object("nbt", wrongDataShape),
			object("nbt", current("1b")),
			pathObject("a[*]", current("1b")),
			pathObject("a", current("broken trailing")),
			JsonParser.parseString("{\"nbt\":\"" + escape(current("{}")) + "\"}").getAsJsonObject(),
			JsonParser.parseString("{\"type\":\"fluid\",\"nbt\":\"" + escape(current("{}")) + "\"}").getAsJsonObject(),
			JsonParser.parseString("{\"nbt_path\":\"a\",\"value\":\"" + escape(current("1")) + "\"}").getAsJsonObject(),
			JsonParser.parseString("{\"type\":\"fluid\",\"nbt_path\":\"a\",\"value\":\"" + escape(current("1")) + "\"}").getAsJsonObject(),
			JsonParser.parseString("{\"type\":\"item\",\"nbt\":3}").getAsJsonObject(),
			JsonParser.parseString("{\"type\":\"item\",\"nbt\":\"" + escape(current("{}")) + "\",\"id\":\"minecraft:stone\"}").getAsJsonObject(),
			JsonParser.parseString("{\"any\":[{\"type\":\"item\",\"id\":\"minecraft:stone\"}],\"nbt\":\"" + escape(current("{}")) + "\"}").getAsJsonObject(),
			JsonParser.parseString("{\"type\":\"item\",\"nbt\":\"" + escape(current("{}")) + "\",\"nbt_path\":\"a\",\"value\":\"" + escape(current("1")) + "\"}").getAsJsonObject())) {
			GroupFilter.Unsupported unsupported = assertInstanceOf(GroupFilter.Unsupported.class, GroupConfig.parseFilter(source));
			assertEquals(source, unsupported.rawJson());
			assertEquals(source, GroupConfig.serializeFilter(unsupported));
		}
	}

	@Test
	void foreignComponentNodesRemainOpaqueBesideNativeNbt() {
		JsonObject source = JsonParser.parseString("""
			{"any":[
			  {"type":"item","component":"minecraft:custom_data","path":"future[*]","value":"1b"},
			  {"type":"item","nbt_path":"value","value":"%s"}
			]}
			""".formatted(escape(current("1b")))).getAsJsonObject();
		GroupFilter.Any parsed = assertInstanceOf(GroupFilter.Any.class, GroupConfig.parseFilter(source));
		assertInstanceOf(GroupFilter.Unsupported.class, parsed.children().get(0));
		assertInstanceOf(GroupFilter.NbtPath.class, parsed.children().get(1));
		assertEquals(source, GroupConfig.serializeFilter(parsed));
	}

	private static JsonObject object(String key, String value) {
		JsonObject object = new JsonObject();
		object.addProperty("type", "item");
		object.addProperty(key, value);
		return object;
	}

	private static JsonObject pathObject(String path, String value) {
		JsonObject object = object("nbt_path", path);
		object.addProperty("value", value);
		return object;
	}

	private static String current(String value) {
		return VersionedDataEnvelope.wrap(
			MinecraftItemDataFormats.NBT_VALUE_1_20_1, new JsonPrimitive(value));
	}

	private static String escape(String value) {
		return new JsonPrimitive(value).toString().substring(1, new JsonPrimitive(value).toString().length() - 1);
	}
}
