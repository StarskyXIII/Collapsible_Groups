package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.ingredient.TagQueryResult;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagQueryEvaluationTest {
	private static final GroupFilter TAG = new GroupFilter.Tag("chemical", "test:tag");
	private static final GroupFilter YES = new GroupFilter.Id("chemical", "test:value");
	private static final GroupFilter NO = new GroupFilter.Id("chemical", "test:other");

	@ParameterizedTest
	@EnumSource(TagQueryResult.class)
	void tagAndNegationPreserveQueryResult(TagQueryResult result) {
		IngredientView view = view(result);
		CompiledFilter.Evaluation expected = switch (result) {
			case MATCH -> CompiledFilter.Evaluation.MATCH;
			case NO_MATCH -> CompiledFilter.Evaluation.NO_MATCH;
			case UNAVAILABLE -> CompiledFilter.Evaluation.UNAVAILABLE;
		};
		assertEquals(expected, CompiledFilter.compile(TAG).evaluate(view));
		assertEquals(result == TagQueryResult.MATCH, CompiledFilter.compile(TAG).matches(view));
		assertEquals(switch (expected) {
			case MATCH -> CompiledFilter.Evaluation.NO_MATCH;
			case NO_MATCH -> CompiledFilter.Evaluation.MATCH;
			case UNAVAILABLE -> CompiledFilter.Evaluation.UNAVAILABLE;
		}, CompiledFilter.compile(new GroupFilter.Not(TAG)).evaluate(view));
		assertEquals(expected, CompiledFilter.compile(new GroupFilter.Not(new GroupFilter.Not(TAG))).evaluate(view));
	}

	@Test
	void unavailableBranchesDoNotEraseDecisiveSiblings() {
		IngredientView view = view(TagQueryResult.UNAVAILABLE);
		for (boolean reverse : List.of(false, true)) {
			assertResult(new GroupFilter.Any(reverse ? List.of(TAG, YES) : List.of(YES, TAG)), view, CompiledFilter.Evaluation.MATCH);
			assertResult(new GroupFilter.All(reverse ? List.of(TAG, NO) : List.of(NO, TAG)), view, CompiledFilter.Evaluation.NO_MATCH);
			assertResult(new GroupFilter.Any(reverse ? List.of(TAG, NO) : List.of(NO, TAG)), view, CompiledFilter.Evaluation.UNAVAILABLE);
			assertResult(new GroupFilter.All(reverse ? List.of(TAG, YES) : List.of(YES, TAG)), view, CompiledFilter.Evaluation.UNAVAILABLE);
		}
		assertResult(new GroupFilter.Not(new GroupFilter.All(List.of(YES, new GroupFilter.Any(List.of(NO, TAG))))), view, CompiledFilter.Evaluation.UNAVAILABLE);
	}

	@Test
	void differentTypeDoesNotQueryUnsupportedIngredient() {
		IngredientView view = new IngredientView() {
			public String ingredientType() { return "chemical"; }
			public ResourceLocation resourceLocation() { return ResourceLocation.parse("test:value"); }
			public boolean hasTag(ResourceLocation tag) { throw new AssertionError("Type mismatch must not query"); }
			public TagQueryResult queryTag(ResourceLocation tag) { throw new AssertionError("Type mismatch must not query"); }
			public boolean matchesExactStack(String encoded) { return false; }
		};
		GroupFilter other = new GroupFilter.Tag("fluid", "test:tag");
		assertResult(other, view, CompiledFilter.Evaluation.NO_MATCH);
		assertResult(new GroupFilter.Not(other), view, CompiledFilter.Evaluation.MATCH);
	}

	@Test
	void legacyBooleanImplementationsRetainTagBehavior() {
		for (String type : List.of("item", "fluid", "chemical")) {
			IngredientView view = new IngredientView() {
				public String ingredientType() { return type; }
				public ResourceLocation resourceLocation() { return ResourceLocation.parse("test:value"); }
				public boolean hasTag(ResourceLocation tag) { return tag.equals(ResourceLocation.parse("test:tag")); }
				public boolean matchesExactStack(String encoded) { return false; }
			};
			assertResult(new GroupFilter.Tag(type, "test:tag"), view, CompiledFilter.Evaluation.MATCH);
			assertResult(new GroupFilter.Tag(type, "test:absent"), view, CompiledFilter.Evaluation.NO_MATCH);
		}
	}

	private static IngredientView view(TagQueryResult result) {
		return new IngredientView() {
			public String ingredientType() { return "chemical"; }
			public ResourceLocation resourceLocation() { return ResourceLocation.parse("test:value"); }
			public boolean hasTag(ResourceLocation tag) { throw new AssertionError("Boolean query loses availability"); }
			public TagQueryResult queryTag(ResourceLocation tag) { return result; }
			public boolean matchesExactStack(String encoded) { return false; }
		};
	}

	private static void assertResult(GroupFilter filter, IngredientView view, CompiledFilter.Evaluation expected) {
		assertEquals(expected, CompiledFilter.compile(filter).evaluate(view));
	}
}
