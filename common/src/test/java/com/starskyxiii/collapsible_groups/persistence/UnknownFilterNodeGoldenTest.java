package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class UnknownFilterNodeGoldenTest {
	private static final IngredientView MATCHING_ITEM = new IngredientView() {
		@Override public String ingredientType() { return "item"; }
		@Override public ResourceLocation resourceLocation() { return ResourceLocation.parse("minecraft:stone"); }
		@Override public boolean hasTag(ResourceLocation tagId) { return false; }
		@Override public boolean matchesExactStack(String encodedStack) { return false; }
	};

	@Test
	void unknownNodePreservesRawSubtreeAndEvaluatesUnavailable() throws IOException {
		String source = readFixture();
		JsonObject sourceFilter = JsonParser.parseString(source).getAsJsonObject().getAsJsonObject("filter");
		GroupDefinition group = GroupConfig.fromJson(source);

		assertNotNull(group);
		GroupFilter.Unsupported unsupported = assertInstanceOf(GroupFilter.Unsupported.class, group.filter());
		assertEquals("future_match", unsupported.recognizedKind());
		assertTrue(group.hasUnavailableFilter());
		assertFalse(group.isStructurallyEditable());
		assertArrayEquals(
			sourceFilter.toString().getBytes(StandardCharsets.UTF_8),
			unsupported.rawJson().toString().getBytes(StandardCharsets.UTF_8)
		);
		assertEquals(CompiledFilter.Evaluation.UNAVAILABLE, group.compiledFilter().evaluate(MATCHING_ITEM));
		assertFalse(group.compiledFilter().matches(MATCHING_ITEM));

		JsonObject serializedFilter = JsonParser.parseString(GroupConfig.toJson(group))
			.getAsJsonObject().getAsJsonObject("filter");
		assertArrayEquals(
			sourceFilter.toString().getBytes(StandardCharsets.UTF_8),
			serializedFilter.toString().getBytes(StandardCharsets.UTF_8)
		);

		GroupDefinition quietMetadataEdit = group.withEnabled(false).withName("Renamed Future Rule");
		JsonObject quietlySavedFilter = JsonParser.parseString(GroupConfig.toJson(quietMetadataEdit))
			.getAsJsonObject().getAsJsonObject("filter");
		assertArrayEquals(
			sourceFilter.toString().getBytes(StandardCharsets.UTF_8),
			quietlySavedFilter.toString().getBytes(StandardCharsets.UTF_8),
			"rename/enable saves must preserve the opaque subtree"
		);
	}

	@Test
	void unavailablePropagatesConservativelyThroughBooleanOperators() {
		GroupFilter unavailable = new GroupFilter.Unsupported(
			JsonParser.parseString("{\"future\":true}").getAsJsonObject(), "future"
		);
		GroupFilter matching = Filters.itemId("minecraft:stone");
		GroupFilter nonMatching = Filters.itemId("minecraft:dirt");

		assertEvaluation(CompiledFilter.Evaluation.UNAVAILABLE, Filters.not(unavailable));
		assertEvaluation(CompiledFilter.Evaluation.MATCH, Filters.any(matching, unavailable));
		assertEvaluation(CompiledFilter.Evaluation.UNAVAILABLE, Filters.any(nonMatching, unavailable));
		assertEvaluation(CompiledFilter.Evaluation.NO_MATCH, Filters.all(nonMatching, unavailable));
		assertEvaluation(CompiledFilter.Evaluation.UNAVAILABLE, Filters.all(matching, unavailable));
	}

	private static void assertEvaluation(CompiledFilter.Evaluation expected, GroupFilter filter) {
		CompiledFilter compiled = CompiledFilter.compile(filter);
		assertEquals(expected, compiled.evaluate(MATCHING_ITEM));
		assertEquals(expected == CompiledFilter.Evaluation.MATCH, compiled.matches(MATCHING_ITEM));
	}

	private static String readFixture() throws IOException {
		try (InputStream stream = UnknownFilterNodeGoldenTest.class.getResourceAsStream(
			"/golden-persistence/unknown-filter-node.json")) {
			assertNotNull(stream);
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
