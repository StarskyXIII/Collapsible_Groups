package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-common coverage for block-tag JSON serialization.
 *
 * <p>Validator assertions for block tags are intentionally not exercised here because
 * {@link com.starskyxiii.collapsible_groups.core.GroupFilterValidator} delegates to
 * {@code ResourceLocation.tryParse(...)}, which is unavailable on the pure-common
 * unit-test runtime classpath.
 */
class GroupConfigBlockTagTest {

	@Test
	void blockTagRecordNullChecks() {
		assertThrows(NullPointerException.class, () -> new GroupFilter.BlockTag(null));
	}

	@Test
	void parseFilterDeserializesBlockTagNode() {
		JsonObject node = JsonParser.parseString("""
			{
				"block_tag": "minecraft:logs"
			}
			""").getAsJsonObject();

		GroupFilter result = GroupConfig.parseFilter(node);

		assertInstanceOf(GroupFilter.BlockTag.class, result);
		assertEquals("minecraft:logs", ((GroupFilter.BlockTag) result).tag());
	}

	@Test
	void serializeFilterWritesBlockTagNode() {
		JsonObject json = GroupConfig.serializeFilter(new GroupFilter.BlockTag("minecraft:logs"));

		assertEquals("minecraft:logs", json.get("block_tag").getAsString());
		assertFalse(json.has("type"));
	}

	@Test
	void blockTagRoundTripPreservesTag() {
		GroupFilter.BlockTag original = new GroupFilter.BlockTag("minecraft:logs");

		JsonObject serialized = GroupConfig.serializeFilter(original);
		GroupFilter parsed = GroupConfig.parseFilter(serialized);

		assertInstanceOf(GroupFilter.BlockTag.class, parsed);
		assertEquals(original.tag(), ((GroupFilter.BlockTag) parsed).tag());
	}
}
