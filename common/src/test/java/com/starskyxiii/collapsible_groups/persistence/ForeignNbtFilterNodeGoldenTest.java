package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.FilterNodeKind;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;


import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForeignNbtFilterNodeGoldenTest {
	private static final IngredientView ITEM = new IngredientView() {
		@Override public String ingredientType() { return "item"; }
		@Override public ResourceLocation resourceLocation() { return ResourceLocation.parse("minecraft:stone"); }
		@Override public boolean hasTag(ResourceLocation tagId) { return false; }
		@Override public boolean matchesExactStack(String encodedStack) { return false; }
	};

	@Test
	void current1201NbtAndNestedNbtPathRemainOpaqueUnavailableAndRoundTripRaw() throws IOException {
		String source = readFixture();
		JsonObject sourceFilter = JsonParser.parseString(source).getAsJsonObject().getAsJsonObject("filter");
		GroupDefinition group = GroupConfig.fromJson(source);

		assertNotNull(group);
		GroupFilter.Any any = assertInstanceOf(GroupFilter.Any.class, group.filter());
		GroupFilter.Unsupported nbt = assertInstanceOf(GroupFilter.Unsupported.class, any.children().get(0));
		assertEquals("nbt", nbt.recognizedKind());
        assertEquals("minecraft:nbt", nbt.rawJson().getAsJsonObject("nbt").get("data_format").getAsString());
		GroupFilter.Not not = assertInstanceOf(GroupFilter.Not.class, any.children().get(1));
		GroupFilter.Unsupported nbtPath = assertInstanceOf(GroupFilter.Unsupported.class, not.child());
		assertEquals("nbt_path", nbtPath.recognizedKind());
        assertEquals("minecraft:nbt", nbtPath.rawJson().getAsJsonObject("value").get("data_format").getAsString());
		assertTrue(group.hasUnavailableFilter());
		assertFalse(group.isStructurallyEditable());
		assertEquals(CompiledFilter.Evaluation.UNAVAILABLE, group.compiledFilter().evaluate(ITEM));

		JsonObject serializedFilter = JsonParser.parseString(GroupConfig.toJson(group))
			.getAsJsonObject().getAsJsonObject("filter");
		assertEquals(sourceFilter.toString(), serializedFilter.toString());
		assertFalse(Arrays.stream(FilterNodeKind.values()).map(Enum::name)
			.anyMatch(name -> name.equals("NBT") || name.equals("NBT_PATH")));
	}

	private static String readFixture() throws IOException {
		try (InputStream stream = ForeignNbtFilterNodeGoldenTest.class.getResourceAsStream(
			"/golden-persistence/v1-1.20-nbt-rules.json")) {
			assertNotNull(stream);
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
