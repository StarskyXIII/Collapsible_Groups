package com.starskyxiii.collapsible_groups.core;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * verifies that {@link CompiledFilter}'s Id set-folding for {@code Any} nodes
 * (1) evaluates in O(1) calls to {@link IngredientView#ingredientType()} /
 * {@link IngredientView#resourceLocation()} regardless of how many Id children were folded, and
 * (2) is behaviorally equivalent to a straightforward linear reference evaluator for every input
 * shape called out in the spec (mixed node kinds, mixed ingredient types, duplicates,
 * case-sensitive/unknown types, raw nested Any, Not(Any), All(Any), residual ordering, empty/
 * single-child Any).
 */
class CompiledFilterIdSetFoldingTest {

	// ------------------------------------------------------------------
	// O(1) call-count regression tests
	// ------------------------------------------------------------------

	@Test
	void idSetFoldLateHitCostsConstantCallsRegardlessOfRunSize() {
		GroupFilter small = buildIdRunAny(5, "modns");
		GroupFilter large = buildIdRunAny(1000, "modns");

		CountingIngredientView smallHit = new CountingIngredientView("item", idAt("modns", 4));
		CountingIngredientView largeHit = new CountingIngredientView("item", idAt("modns", 999));

		assertTrue(CompiledFilter.compile(small).matches(smallHit));
		assertTrue(CompiledFilter.compile(large).matches(largeHit));

		assertEquals(1, smallHit.resourceLocationCalls);
		assertEquals(1, smallHit.ingredientTypeCalls);
		assertEquals(smallHit.resourceLocationCalls, largeHit.resourceLocationCalls, "resourceLocation() call count must not scale with run size");
		assertEquals(smallHit.ingredientTypeCalls, largeHit.ingredientTypeCalls, "ingredientType() call count must not scale with run size");
	}

	@Test
	void idSetFoldMissCostsConstantCallsRegardlessOfRunSize() {
		GroupFilter small = buildIdRunAny(5, "modns");
		GroupFilter large = buildIdRunAny(1000, "modns");
		Identifier notInSet = Identifier.parse("modns:not_in_the_set");

		CountingIngredientView smallMiss = new CountingIngredientView("item", notInSet);
		CountingIngredientView largeMiss = new CountingIngredientView("item", notInSet);

		assertFalse(CompiledFilter.compile(small).matches(smallMiss));
		assertFalse(CompiledFilter.compile(large).matches(largeMiss));

		assertEquals(1, smallMiss.resourceLocationCalls);
		assertEquals(1, smallMiss.ingredientTypeCalls);
		assertEquals(smallMiss.resourceLocationCalls, largeMiss.resourceLocationCalls);
		assertEquals(smallMiss.ingredientTypeCalls, largeMiss.ingredientTypeCalls);
	}

	@Test
	void idSetFoldTypeMismatchCostsConstantCallsRegardlessOfRunSize() {
		GroupFilter large = buildIdRunAny(1000, "modns");
		// resourceLocation happens to equal a real entry's location, but under the wrong type.
		CountingIngredientView view = new CountingIngredientView("fluid", idAt("modns", 500));

		assertFalse(CompiledFilter.compile(large).matches(view));

		assertEquals(1, view.resourceLocationCalls);
		assertEquals(1, view.ingredientTypeCalls);
	}

	@Test
	void idSetFoldNullIdentifierShortCircuitsBeforeIngredientTypeLookup() {
		GroupFilter large = buildIdRunAny(1000, "modns");
		CountingIngredientView view = new CountingIngredientView("item", null);

		assertFalse(CompiledFilter.compile(large).matches(view));

		assertEquals(1, view.resourceLocationCalls);
		assertEquals(0, view.ingredientTypeCalls, "null resourceLocation() must short-circuit before ingredientType() is consulted");
	}

	// ------------------------------------------------------------------
	// Equivalence matrix: folded CompiledFilter vs. hand-written linear reference
	// ------------------------------------------------------------------

	@Test
	void largeAnyMixingIdTagNotAllFoldsEquivalentlyToLinearReference() {
		// Non-Id nodes flank and interrupt Id runs; one run mixes ingredient types and a
		// duplicate id; a differently-cased type and an unknown type are preserved distinctly;
		// a raw (non-normalized) nested Any(Any(...)) sits at the tail. (ExactStack is deliberately
		// excluded here: folds it into a node whose evaluation decodes via a live
		// registry/ItemStack that cannot be bootstrapped in a headless unit test - see
		// CompiledFilterExactStackFoldingTest for the decode-free structural coverage.)
		GroupFilter tree = new GroupFilter.Any(List.of(
			Filters.tag("item", "forge:ores"),
			Filters.id("item", "modns:ore_0"),
			Filters.id("item", "modns:ore_1"),
			Filters.id("fluid", "modns:fluid_0"),
			Filters.id("item", "modns:ore_0"), // duplicate within the run
			Filters.id("item", "modns:ore_2"),
			Filters.id("Item", "modns:ore_case_sensitive"), // distinct canonical bucket ("Item" != "item")
			Filters.id("unknown_type", "modns:ore_unknown"), // unregistered type preserved as-is
			new GroupFilter.Not(new GroupFilter.Any(List.of(Filters.id("item", "modns:ore_3"), Filters.id("item", "modns:ore_4")))),
			new GroupFilter.Any(List.of(Filters.id("item", "modns:ore_5"), new GroupFilter.Any(List.of(Filters.id("item", "modns:ore_6")))))
		));

		CompiledFilter compiled = CompiledFilter.compile(tree);

		// Tag branch hit.
		MatrixIngredientView tagHit = new MatrixIngredientView("item", rl("modns:something_else"), Set.of(rl("forge:ores")), false);
		assertTrue(compiled.matches(tagHit));
		assertEquivalent(tree, compiled, tagHit);

		// Id-set hit (item run).
		MatrixIngredientView idHit = new MatrixIngredientView("item", rl("modns:ore_2"), Set.of(), false);
		assertTrue(compiled.matches(idHit));
		assertEquivalent(tree, compiled, idHit);

		// Id-set hit (fluid entry inside the same run, different ingredient type bucket).
		MatrixIngredientView fluidHit = new MatrixIngredientView("fluid", rl("modns:fluid_0"), Set.of(), false);
		assertTrue(compiled.matches(fluidHit));
		assertEquivalent(tree, compiled, fluidHit);

		// Case-sensitive type bucket: the "Item"-typed id sits in its own bucket. Note the
		// trailing Not(Any(ore_3, ore_4)) branch is true for any view that isn't exactly
		// type "item" at resourceLocation ore_3/ore_4, so both probes below evaluate to true
		// overall - see caseSensitiveAndUnknownTypeBucketsAreDistinct() for an isolated check
		// that the "Item"/"item"/"unknown_type" buckets don't cross-match each other.
		MatrixIngredientView caseSensitiveHit = new MatrixIngredientView("Item", rl("modns:ore_case_sensitive"), Set.of(), false);
		assertTrue(compiled.matches(caseSensitiveHit));
		assertEquivalent(tree, compiled, caseSensitiveHit);
		MatrixIngredientView caseSensitiveMiss = new MatrixIngredientView("item", rl("modns:ore_case_sensitive"), Set.of(), false);
		assertTrue(compiled.matches(caseSensitiveMiss));
		assertEquivalent(tree, compiled, caseSensitiveMiss);

		// Unknown type preserved verbatim.
		MatrixIngredientView unknownTypeHit = new MatrixIngredientView("unknown_type", rl("modns:ore_unknown"), Set.of(), false);
		assertTrue(compiled.matches(unknownTypeHit));
		assertEquivalent(tree, compiled, unknownTypeHit);

		// Not(Any(ore_3, ore_4)) branch is false only when the view IS ore_3/ore_4 and nothing
		// else matches - this is the one input where the whole filter evaluates to false.
		MatrixIngredientView notBranchFalse = new MatrixIngredientView("item", rl("modns:ore_3"), Set.of(), false);
		assertFalse(compiled.matches(notBranchFalse));
		assertEquivalent(tree, compiled, notBranchFalse);

		// Raw nested Any(Any(...)) tail hit.
		MatrixIngredientView nestedAnyHit = new MatrixIngredientView("item", rl("modns:ore_6"), Set.of(), false);
		assertTrue(compiled.matches(nestedAnyHit));
		assertEquivalent(tree, compiled, nestedAnyHit);

		// Fully unrelated item-typed view: Not(Any(ore_3, ore_4)) is vacuously true here, so the
		// overall result is true - still must agree with the reference evaluator.
		MatrixIngredientView unrelated = new MatrixIngredientView("item", rl("modns:totally_unrelated"), Set.of(), false);
		assertEquivalent(tree, compiled, unrelated);
	}

	@Test
	void caseSensitiveAndUnknownTypeBucketsAreDistinct() {
		// Same run, three ids under three "types" that must not cross-match: an exact-cased
		// canonical type, a differently-cased variant, and an unregistered ("unknown") type
		// preserved verbatim by canonicalType()'s fallback.
		GroupFilter tree = new GroupFilter.Any(List.of(
			Filters.id("item", "modns:item_entry"),
			Filters.id("Item", "modns:capitalized_entry"),
			Filters.id("unknown_type", "modns:unknown_entry")
		));
		CompiledFilter compiled = CompiledFilter.compile(tree);

		assertTrue(compiled.matches(new MatrixIngredientView("item", rl("modns:item_entry"), Set.of(), false)));
		assertFalse(compiled.matches(new MatrixIngredientView("Item", rl("modns:item_entry"), Set.of(), false)));
		assertFalse(compiled.matches(new MatrixIngredientView("unknown_type", rl("modns:item_entry"), Set.of(), false)));

		assertTrue(compiled.matches(new MatrixIngredientView("Item", rl("modns:capitalized_entry"), Set.of(), false)));
		assertFalse(compiled.matches(new MatrixIngredientView("item", rl("modns:capitalized_entry"), Set.of(), false)));

		assertTrue(compiled.matches(new MatrixIngredientView("unknown_type", rl("modns:unknown_entry"), Set.of(), false)));
		assertFalse(compiled.matches(new MatrixIngredientView("item", rl("modns:unknown_entry"), Set.of(), false)));

		for (MatrixIngredientView view : List.of(
			new MatrixIngredientView("item", rl("modns:item_entry"), Set.of(), false),
			new MatrixIngredientView("Item", rl("modns:item_entry"), Set.of(), false),
			new MatrixIngredientView("unknown_type", rl("modns:item_entry"), Set.of(), false),
			new MatrixIngredientView("Item", rl("modns:capitalized_entry"), Set.of(), false),
			new MatrixIngredientView("item", rl("modns:capitalized_entry"), Set.of(), false),
			new MatrixIngredientView("unknown_type", rl("modns:unknown_entry"), Set.of(), false),
			new MatrixIngredientView("item", rl("modns:unknown_entry"), Set.of(), false)
		)) {
			assertEquivalent(tree, compiled, view);
		}
	}

	@Test
	void notWrappingAnyFoldsEquivalentlyToLinearReference() {
		GroupFilter tree = new GroupFilter.Not(new GroupFilter.Any(List.of(
			Filters.id("item", "modns:ore_0"),
			Filters.id("item", "modns:ore_1"),
			Filters.tag("item", "forge:ores")
		)));
		CompiledFilter compiled = CompiledFilter.compile(tree);

		for (MatrixIngredientView view : List.of(
			new MatrixIngredientView("item", rl("modns:ore_0"), Set.of(), false),
			new MatrixIngredientView("item", rl("modns:ore_9"), Set.of(), false),
			new MatrixIngredientView("item", rl("modns:ore_9"), Set.of(rl("forge:ores")), false)
		)) {
			assertEquivalent(tree, compiled, view);
		}
	}

	@Test
	void allWrappingAnyFoldsEquivalentlyToLinearReference() {
		GroupFilter tree = new GroupFilter.All(List.of(
			Filters.tag("item", "forge:ores"),
			new GroupFilter.Any(List.of(Filters.id("item", "modns:ore_0"), Filters.id("item", "modns:ore_1")))
		));
		CompiledFilter compiled = CompiledFilter.compile(tree);

		for (MatrixIngredientView view : List.of(
			new MatrixIngredientView("item", rl("modns:ore_0"), Set.of(rl("forge:ores")), false),
			new MatrixIngredientView("item", rl("modns:ore_0"), Set.of(), false), // id matches, tag doesn't
			new MatrixIngredientView("item", rl("modns:ore_9"), Set.of(rl("forge:ores")), false) // tag matches, id doesn't
		)) {
			assertEquivalent(tree, compiled, view);
		}
	}

	@Test
	void idRunsFlankedByNonIdNodesPreserveOrderAndEquivalence() {
		// A non-Id node (Tag) sits between two Id runs, so the runs must fold into two separate
		// IdSetNodes with the interrupter preserved in place. (A Tag is used rather than an
		// ExactStack because ExactStack folding decodes via a live registry unavailable to headless
		// unit tests; ExactStack run segmentation is covered decode-free in
		// CompiledFilterExactStackFoldingTest.)
		GroupFilter tree = new GroupFilter.Any(List.of(
			Filters.tag("item", "forge:ores"),
			Filters.id("item", "modns:a"),
			Filters.id("item", "modns:b"),
			Filters.tag("item", "forge:middle"),
			Filters.id("item", "modns:c"),
			Filters.id("item", "modns:d"),
			Filters.tag("item", "forge:tail")
		));
		CompiledFilter compiled = CompiledFilter.compile(tree);

		for (MatrixIngredientView view : List.of(
			new MatrixIngredientView("item", rl("modns:a"), Set.of(), false),
			new MatrixIngredientView("item", rl("modns:d"), Set.of(), false),
			new MatrixIngredientView("item", rl("modns:x"), Set.of(rl("forge:tail")), false),
			new MatrixIngredientView("item", rl("modns:x"), Set.of(rl("forge:middle")), false),
			new MatrixIngredientView("item", rl("modns:x"), Set.of(), false)
		)) {
			assertEquivalent(tree, compiled, view);
		}
	}

	@Test
	void emptyAnyNeverMatches() {
		GroupFilter tree = new GroupFilter.Any(List.of());
		CompiledFilter compiled = CompiledFilter.compile(tree);

		MatrixIngredientView view = new MatrixIngredientView("item", rl("modns:anything"), Set.of(), true);
		assertFalse(compiled.matches(view));
		assertEquivalent(tree, compiled, view);
	}

	@Test
	void singleIdChildFoldsToIdSetAndStaysEquivalent() {
		GroupFilter tree = new GroupFilter.Any(List.of(Filters.id("item", "modns:only")));
		CompiledFilter compiled = CompiledFilter.compile(tree);

		assertTrue(compiled.matches(new MatrixIngredientView("item", rl("modns:only"), Set.of(), false)));
		assertFalse(compiled.matches(new MatrixIngredientView("item", rl("modns:other"), Set.of(), false)));
		assertEquivalent(tree, compiled, new MatrixIngredientView("item", rl("modns:only"), Set.of(), false));
		assertEquivalent(tree, compiled, new MatrixIngredientView("item", rl("modns:other"), Set.of(), false));
	}

	@Test
	void singleNonIdChildIsNotFoldedAndStaysEquivalent() {
		GroupFilter tree = new GroupFilter.Any(List.of(Filters.tag("item", "forge:ores")));
		CompiledFilter compiled = CompiledFilter.compile(tree);

		assertTrue(compiled.matches(new MatrixIngredientView("item", rl("modns:x"), Set.of(rl("forge:ores")), false)));
		assertFalse(compiled.matches(new MatrixIngredientView("item", rl("modns:x"), Set.of(), false)));
		assertEquivalent(tree, compiled, new MatrixIngredientView("item", rl("modns:x"), Set.of(rl("forge:ores")), false));
		assertEquivalent(tree, compiled, new MatrixIngredientView("item", rl("modns:x"), Set.of(), false));
	}

	@Test
	void sourceReturnsOriginalFilterInstance() {
		GroupFilter tree = new GroupFilter.Any(List.of(Filters.id("item", "modns:a"), Filters.id("item", "modns:b")));
		CompiledFilter compiled = CompiledFilter.compile(tree);

		assertSame(tree, compiled.source());
	}

	// ------------------------------------------------------------------
	// Test fixtures
	// ------------------------------------------------------------------

	private static GroupFilter buildIdRunAny(int count, String namespace) {
		List<GroupFilter> children = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			children.add(Filters.id("item", idAt(namespace, i).toString()));
		}
		return new GroupFilter.Any(List.copyOf(children));
	}

	private static Identifier idAt(String namespace, int index) {
		return Identifier.parse(namespace + ":ore_" + String.format("%04d", index));
	}

	private static Identifier rl(String value) {
		return Identifier.parse(value);
	}

	private static void assertEquivalent(GroupFilter filter, CompiledFilter compiled, IngredientView view) {
		assertEquals(referenceMatches(filter, view), compiled.matches(view), () -> "mismatch for view " + view);
	}

	/** Hand-written linear evaluator mirroring pre-CompiledFilter semantics; used as ground truth. */
	private static boolean referenceMatches(GroupFilter filter, IngredientView view) {
		return switch (filter) {
			case GroupFilter.Any any -> any.children().stream().anyMatch(child -> referenceMatches(child, view));
			case GroupFilter.All all -> all.children().stream().allMatch(child -> referenceMatches(child, view));
			case GroupFilter.Not not -> !referenceMatches(not.child(), view);
			case GroupFilter.Id id -> sameType(id.ingredientType(), view) && Identifier.parse(id.id()).equals(view.resourceLocation());
			case GroupFilter.Tag tag -> sameType(tag.ingredientType(), view) && view.hasTag(Identifier.parse(tag.tag()));
			case GroupFilter.ExactStack exactStack -> sameType("item", view) && view.matchesExactStack(exactStack.encodedStack());
			default -> throw new UnsupportedOperationException("referenceMatches: filter kind not needed by this test matrix: " + filter);
		};
	}

	private static boolean sameType(String ingredientType, IngredientView view) {
		// IngredientTypeRegistry has no registrations in the unit test classpath, so
		// canonicalization is the identity function here - matching CompiledFilter's own
		// canonicalType() fallback for unregistered/unknown types.
		return ingredientType.equals(view.ingredientType());
	}

	private static final class CountingIngredientView implements IngredientView {
		private final String ingredientType;
		private final Identifier resourceLocation;
		private int ingredientTypeCalls = 0;
		private int resourceLocationCalls = 0;

		CountingIngredientView(String ingredientType, Identifier resourceLocation) {
			this.ingredientType = ingredientType;
			this.resourceLocation = resourceLocation;
		}

		@Override
		public String ingredientType() {
			ingredientTypeCalls++;
			return ingredientType;
		}

		@Override
		public Identifier resourceLocation() {
			resourceLocationCalls++;
			return resourceLocation;
		}

		@Override
		public boolean hasTag(Identifier tagId) {
			return false;
		}

		@Override
		public boolean matchesExactStack(String encodedStack) {
			return false;
		}
	}

	private record MatrixIngredientView(String ingredientType, Identifier resourceLocation, Set<Identifier> tags, boolean exactStackMatches) implements IngredientView {
		@Override
		public boolean hasTag(Identifier tagId) {
			return tags.contains(tagId);
		}

		@Override
		public boolean matchesExactStack(String encodedStack) {
			return exactStackMatches;
		}
	}
}
