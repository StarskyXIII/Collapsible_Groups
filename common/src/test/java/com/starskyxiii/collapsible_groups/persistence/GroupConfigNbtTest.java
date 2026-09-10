package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.group.GroupDocumentFormat;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GroupConfigNbtTest {
	@Test void rootAndPathUseSharedNbtFormatWithNativeTypes() {
		for (String value : List.of("1b", "1", "1L", "[B;1b,2b]", "[I;1,2]", "[L;1L,2L]", "\"quoted \\\" and \\\\ slash\"")) {
			GroupFilter.NbtPath original = new GroupFilter.NbtPath("list[0]", ItemDataPayload.nbt(value));
			JsonObject json = GroupConfig.serializeFilter(original, GroupDocumentFormat.V1);
			assertEquals("minecraft:nbt", json.getAsJsonObject("value").get("data_format").getAsString());
			assertEquals(value, json.getAsJsonObject("value").get("data").getAsString());
			assertEquals(original, GroupConfig.parseFilter(json, GroupDocumentFormat.V1));
		}
		var root = new GroupFilter.Nbt(ItemDataPayload.nbt("{value:1b}"));
		assertEquals(root, GroupConfig.parseFilter(GroupConfig.serializeFilter(root, GroupDocumentFormat.V1), GroupDocumentFormat.V1));
	}

	@Test void malformedAndForeignNodesRetainEntireRawSubtrees() {
		for (String json : List.of(
			"{\"type\":\"item\",\"nbt\":\"{a:1b}\"}",
			"{\"type\":\"item\",\"nbt\":{\"data_format\":\"minecraft:nbt\",\"data\":1}}",
			"{\"type\":\"item\",\"nbt\":{\"data_format\":\"minecraft:data_component\",\"data\":{}}}",
			"{\"type\":\"item\",\"nbt\":{\"data_format\":\"minecraft:nbt\"}}",
			"{\"type\":\"item\",\"nbt\":{\"data_format\":\"minecraft:nbt\",\"data\":\"1b\"}}",
			"{\"type\":\"item\",\"nbt_path\":\"a[*]\",\"value\":{\"data_format\":\"minecraft:nbt\",\"data\":\"1b\"}}",
			"{\"type\":\"item\",\"nbt_path\":\"a\",\"value\":{\"data_format\":\"minecraft:nbt\",\"data\":\"bad trailing\"}}",
			"{\"type\":\"item\",\"nbt\":{\"data_format\":\"minecraft:nbt\",\"data\":\"{}\"},\"future\":1}"
		)) {
			JsonObject raw = JsonParser.parseString(json).getAsJsonObject();
			GroupFilter parsed = GroupConfig.parseFilter(raw, GroupDocumentFormat.V1);
			assertInstanceOf(GroupFilter.Unsupported.class, parsed);
			assertEquals(raw, GroupConfig.serializeFilter(parsed, GroupDocumentFormat.V1));
		}
	}

	@Test void legacyCannotRepresentNewNbtOrExactRules() {
		for (GroupFilter filter : List.of(new GroupFilter.Nbt(ItemDataPayload.nbt("{}")),
				new GroupFilter.NbtPath("a", ItemDataPayload.nbt("1b")),
				new GroupFilter.ExactStack(ItemDataPayload.nbt("{id:\"minecraft:stone\",Count:1b}")))) {
			assertThrows(IllegalArgumentException.class, () -> GroupConfig.serializeFilter(filter, GroupDocumentFormat.LEGACY));
			var json = GroupConfig.serializeFilter(filter, GroupDocumentFormat.V1);
			assertInstanceOf(GroupFilter.Unsupported.class, GroupConfig.parseFilter(json, GroupDocumentFormat.LEGACY));
		}
	}
}
