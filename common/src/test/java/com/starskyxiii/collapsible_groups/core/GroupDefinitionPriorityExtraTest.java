package com.starskyxiii.collapsible_groups.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GroupDefinitionPriorityExtraTest {
	@Test
	void oldConstructorsUseDefaultPriorityAndEmptyExtra() {
		GroupDefinition group = new GroupDefinition(
			"old_constructor",
			"Old Constructor",
			true,
			Filters.itemId("minecraft:stone")
		);

		assertEquals(0, group.priority());
		assertFalse(group.hasExtra());
		assertEquals(new JsonObject(), group.extra());
	}

	@Test
	void copyMethodsPreservePriorityAndExtra() {
		JsonObject extra = new JsonObject();
		extra.addProperty("unknown_key", "keep me");
		GroupTheme theme = new GroupTheme("#FFAA00", "#11111111", "#22222222", "#33333333", "#44444444");
		GroupDefinition group = new GroupDefinition(
			"metadata_group",
			"Metadata Group",
			true,
			Filters.itemId("minecraft:stone"),
			List.of("minecraft:diamond"),
			theme,
			12,
			extra
		);

		assertPreserved(group, group.withEnabled(false));
		assertPreserved(group, group.withName("Renamed"));
		assertPreserved(group, group.withDisplayName(group.displayName()));
		assertPreserved(group, group.withIconIds(List.of("minecraft:emerald")));
		assertPreserved(group, group.withFilter(Filters.itemId("minecraft:dirt")));
		assertPreserved(group, group.withTheme(GroupTheme.EMPTY));
	}

	@Test
	void extraIsDefensivelyCopied() {
		JsonObject extra = new JsonObject();
		extra.addProperty("stored", "original");
		GroupDefinition group = new GroupDefinition(
			"defensive_extra",
			"Defensive Extra",
			true,
			Filters.itemId("minecraft:stone"),
			List.of(),
			GroupTheme.EMPTY,
			1,
			extra
		);

		extra.addProperty("stored", "mutated source");
		JsonObject returned = group.extra();
		returned.addProperty("stored", "mutated accessor");

		assertEquals("original", group.extra().get("stored").getAsString());
	}

	private static void assertPreserved(GroupDefinition expected, GroupDefinition actual) {
		assertEquals(expected.priority(), actual.priority());
		assertEquals(expected.extra(), actual.extra());
	}
}
