package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.FilterNodeKind;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataFormat;
import com.starskyxiii.collapsible_groups.internal.version.data.VersionedDataEnvelope;
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

class LegacyNbtFilterNodeGoldenTest {
	private static final ItemDataFormat NBT_VALUE_1_20_1 = new ItemDataFormat(
		"collapsible_groups:nbt_value", 1, "minecraft:nbt_snbt", "1.20.1");
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
		assertEquals(VersionedDataEnvelope.Support.CURRENT, VersionedDataEnvelope.inspect(
			nbt.rawJson().get("nbt").getAsString(), NBT_VALUE_1_20_1).support());
		GroupFilter.Not not = assertInstanceOf(GroupFilter.Not.class, any.children().get(1));
		GroupFilter.Unsupported nbtPath = assertInstanceOf(GroupFilter.Unsupported.class, not.child());
		assertEquals("nbt_path", nbtPath.recognizedKind());
		assertEquals(VersionedDataEnvelope.Support.CURRENT, VersionedDataEnvelope.inspect(
			nbtPath.rawJson().get("value").getAsString(), NBT_VALUE_1_20_1).support());
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
		try (InputStream stream = LegacyNbtFilterNodeGoldenTest.class.getResourceAsStream(
			"/golden-persistence/legacy-1.20-nbt-rules.json")) {
			assertNotNull(stream);
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
