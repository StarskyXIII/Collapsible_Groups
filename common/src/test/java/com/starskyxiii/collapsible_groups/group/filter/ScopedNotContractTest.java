package com.starskyxiii.collapsible_groups.group.filter;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.ingredient.TagQueryResult;
import com.starskyxiii.collapsible_groups.viewer.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.starskyxiii.collapsible_groups.group.filter.CompiledFilter.Evaluation.*;
import static org.junit.jupiter.api.Assertions.*;

class ScopedNotContractTest {

	private static final GroupFilter WATER = new GroupFilter.Id("fluid", "minecraft:water");
	private static final GroupFilter STONE = new GroupFilter.Id("item", "minecraft:stone");
	private static final GroupFilter UNKNOWN = new GroupFilter.Unsupported(new JsonObject(), "future");
	private static final List<IngredientView> VIEWS = List.of(
		view("fluid", "minecraft:water"), view("fluid", "minecraft:lava"),
		view("item", "minecraft:stone"), view("item", "minecraft:water"),
		view("test:chemical", "minecraft:water"), view("test:bee", "minecraft:water"));

	@Test void agreedExamplesExcludeOnlyWithinMentionedTypes() {
		assertMatches(new GroupFilter.Not(WATER), 1);
		assertMatches(new GroupFilter.Not(new GroupFilter.Any(List.of(WATER, STONE))), 1, 3);
		assertMatches(new GroupFilter.Not(new GroupFilter.All(List.of(WATER, STONE))), 0, 1, 2, 3);
		assertMatches(new GroupFilter.Not(new GroupFilter.Not(WATER)), 0);
		assertMatches(new GroupFilter.Not(new GroupFilter.Id("test:chemical", "test:absent")), 4);
	}

	@Test void unknownAndEmptyDomainsNeverBecomeGlobalComplements() {
		for (GroupFilter child : List.of(UNKNOWN, new GroupFilter.Any(List.of()), new GroupFilter.All(List.of()))) {
			for (IngredientView view : VIEWS) {
				assertEquals(UNAVAILABLE, CompiledFilter.compile(new GroupFilter.Not(child)).evaluate(view));
				assertEquals(UNAVAILABLE, CompiledFilter.compile(new GroupFilter.Not(new GroupFilter.Not(child))).evaluate(view));
			}
		}
		assertEquals(MATCH, CompiledFilter.compile(new GroupFilter.Not(new GroupFilter.All(List.of(WATER, UNKNOWN)))).evaluate(VIEWS.get(1)));
		assertEquals(UNAVAILABLE, CompiledFilter.compile(new GroupFilter.Not(new GroupFilter.Any(List.of(WATER, UNKNOWN)))).evaluate(VIEWS.get(1)));
		assertEquals(NO_MATCH, CompiledFilter.compile(new GroupFilter.Not(new GroupFilter.All(List.of(WATER, UNKNOWN)))).evaluate(VIEWS.get(2)));
	}

	@Test void existingExplicitAliasesStayWithinTheirDeclaredGenericType() {
		String canonical = "test:scoped_not_chemical";
		String alias = "test:scoped_not_alias";
		IngredientTypeIds.registerCanonical(canonical);
		IngredientTypeIds.registerAlias(alias, canonical);
		for (String ruleType : List.of(alias, canonical)) {
			CompiledFilter filter = CompiledFilter.compile(new GroupFilter.Not(new GroupFilter.Id(ruleType, "test:excluded")));
			for (String viewType : List.of(alias, canonical)) {
				assertEquals(MATCH, filter.evaluate(view(viewType, "test:included")));
				assertEquals(NO_MATCH, filter.evaluate(view(viewType, "test:excluded")));
				assertTrue(filter.candidateTypes().contains(viewType));
			}
			assertEquals(NO_MATCH, filter.evaluate(view("test:bee", "test:included")));
			assertEquals(NO_MATCH, filter.evaluate(view("test:unregistered_alias", "test:included")));
		}
	}

	@Test void rawNormalizedCompiledAndCandidateResultsAgreeWithIndependentReference() {
		Random random = new Random(20260906L);
		List<GroupFilter> leaves = List.of(WATER, STONE, UNKNOWN,
			new GroupFilter.Tag("test:chemical", "test:unavailable"),
			new GroupFilter.Tag("fluid", "test:match"),
			new GroupFilter.Namespace("item", "minecraft"),
			new GroupFilter.Id("test:bee", "test:absent"));
		for (int i = 0; i < 500; i++) {
			GroupFilter raw = generate(random, leaves, 4);
			assertReference(raw, true);
		}
		for (GroupFilter empty : List.of(new GroupFilter.Any(List.of()), new GroupFilter.All(List.of()))) {
			assertReference(empty, false);
			assertReference(new GroupFilter.Not(new GroupFilter.Not(empty)), false);
			assertReference(new GroupFilter.Not(new GroupFilter.Not(new GroupFilter.Any(List.of(STONE, empty)))), false);
		}
	}

	private static GroupFilter generate(Random random, List<GroupFilter> leaves, int depth) {
		if (depth == 0 || random.nextInt(4) == 0) return leaves.get(random.nextInt(leaves.size()));
		GroupFilter left = generate(random, leaves, depth - 1);
		return switch (random.nextInt(3)) {
			case 0 -> new GroupFilter.Not(left);
			case 1 -> new GroupFilter.Any(List.of(left, generate(random, leaves, depth - 1)));
			default -> new GroupFilter.All(List.of(left, generate(random, leaves, depth - 1)));
		};
	}

	private static void assertReference(GroupFilter raw, boolean validDefinition) {
		CompiledFilter compiled = CompiledFilter.compile(raw);
		CompiledFilter normalized = CompiledFilter.compile(GroupFilterNormalizer.normalize(raw));
		for (IngredientView view : VIEWS) {
			var expected = reference(raw, view);
			assertEquals(expected, compiled.evaluate(view), () -> raw + " / " + view.ingredientType());
			assertEquals(expected, normalized.evaluate(view), () -> "normalized " + raw + " / " + view.ingredientType());
			if (expected == MATCH) assertTrue(compiled.candidateTypes().contains(view.ingredientType()), "candidate scope " + raw);
		}
		if (!validDefinition) return;
		GroupDefinition group = new GroupDefinition("audit", "Audit", true, raw);
		List<ViewerIngredient<Integer>> entries = new ArrayList<>();
		for (int i = 0; i < VIEWS.size(); i++) {
			IngredientView view = VIEWS.get(i);
			var kind = switch (view.ingredientType()) {
				case "item" -> ViewerIngredient.Kind.ITEM;
				case "fluid" -> ViewerIngredient.Kind.FLUID;
				default -> ViewerIngredient.Kind.GENERIC;
			};
			entries.add(new ViewerIngredient<>(new ViewerIngredientIdentity(view.ingredientType(), "entry-" + i), kind, i, view));
		}
		var candidates = GroupProjectionEngine.buildCandidateIndex(new ViewerIngredientUniverse<>(entries), List.of(group));
		for (var entry : entries) {
			boolean expected = reference(raw, entry.view()) == MATCH;
			assertEquals(expected, candidates.candidates().containsKey(entry.identity()), () -> "candidate " + raw + " / " + entry.view().ingredientType());
			assertEquals(expected, group.compiledFilter().matches(entry.view()));
		}
	}

	private static void assertMatches(GroupFilter filter, Integer... expected) {
		List<Integer> matches = new ArrayList<>();
		for (int i = 0; i < VIEWS.size(); i++) if (CompiledFilter.compile(filter).matches(VIEWS.get(i))) matches.add(i);
		assertEquals(List.of(expected), matches);
	}

	private static CompiledFilter.Evaluation reference(GroupFilter node, IngredientView view) {
		if (node instanceof GroupFilter.Not not) {
			Set<String> scope = new HashSet<>();
			collectTypes(not.child(), scope);
			if (scope.isEmpty()) return UNAVAILABLE;
			if (!scope.contains(view.ingredientType())) return NO_MATCH;
			var child = reference(not.child(), view);
			return child == UNAVAILABLE ? UNAVAILABLE : child == MATCH ? NO_MATCH : MATCH;
		}
		if (node instanceof GroupFilter.Any any) {
			Set<CompiledFilter.Evaluation> results = new HashSet<>();
			any.children().forEach(child -> results.add(reference(child, view)));
			return results.contains(MATCH) ? MATCH : results.contains(UNAVAILABLE) ? UNAVAILABLE : NO_MATCH;
		}
		if (node instanceof GroupFilter.All all) {
			Set<CompiledFilter.Evaluation> results = new HashSet<>();
			all.children().forEach(child -> results.add(reference(child, view)));
			return results.contains(NO_MATCH) ? NO_MATCH : results.contains(UNAVAILABLE) ? UNAVAILABLE : MATCH;
		}
		if (node instanceof GroupFilter.Unsupported) return UNAVAILABLE;
		if (node instanceof GroupFilter.Id id) return id.ingredientType().equals(view.ingredientType()) && id.id().equals(String.valueOf(view.resourceLocation())) ? MATCH : NO_MATCH;
		if (node instanceof GroupFilter.Namespace ns) return ns.ingredientType().equals(view.ingredientType()) && ns.namespace().equals(view.resourceLocation().getNamespace()) ? MATCH : NO_MATCH;
		if (node instanceof GroupFilter.Tag tag) {
			if (!tag.ingredientType().equals(view.ingredientType())) return NO_MATCH;
			return switch (view.queryTag(ResourceLocation.parse(tag.tag()))) {
				case MATCH -> MATCH;
				case NO_MATCH -> NO_MATCH;
				case UNAVAILABLE -> UNAVAILABLE;
			};
		}
		throw new AssertionError("Unsupported reference fixture: " + node);
	}

	private static void collectTypes(GroupFilter node, Set<String> types) {
		if (node instanceof GroupFilter.Any any) any.children().forEach(child -> collectTypes(child, types));
		else if (node instanceof GroupFilter.All all) all.children().forEach(child -> collectTypes(child, types));
		else if (node instanceof GroupFilter.Not not) collectTypes(not.child(), types);
		else if (node instanceof GroupFilter.Id id) types.add(id.ingredientType());
		else if (node instanceof GroupFilter.Tag tag) types.add(tag.ingredientType());
		else if (node instanceof GroupFilter.Namespace ns) types.add(ns.ingredientType());
		else if (!(node instanceof GroupFilter.Unsupported)) throw new AssertionError(node);
	}

	private static IngredientView view(String type, String id) {
		return new IngredientView() {
			public String ingredientType() { return type; }
			public ResourceLocation resourceLocation() { return ResourceLocation.parse(id); }
			public boolean hasTag(ResourceLocation tag) { return tag.getPath().equals("match"); }
			public TagQueryResult queryTag(ResourceLocation tag) { return tag.getPath().equals("unavailable") ? TagQueryResult.UNAVAILABLE : hasTag(tag) ? TagQueryResult.MATCH : TagQueryResult.NO_MATCH; }
			public boolean matchesExactStack(String encoded) { return false; }
		};
	}
}
