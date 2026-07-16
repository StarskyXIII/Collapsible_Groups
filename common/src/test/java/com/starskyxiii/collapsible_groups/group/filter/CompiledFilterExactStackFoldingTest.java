package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * structural coverage for {@link CompiledFilter}'s {@code ExactStack} run folding.
 *
 * <p><b>Testability boundary:</b> a folded exact-stack node only decodes its selectors (and deep-
 * compares candidates) when it evaluates an {@code item}-typed view, and decoding goes through
 * {@code ItemStack.STRICT_SINGLE_ITEM_CODEC} + a live client registry, neither of which can be
 * bootstrapped in a headless unit test (see {@code GroupManagerCardTest}'s note on {@code
 * ItemStack.EMPTY}). Touching those statics fails class initialization
 * ({@code ExceptionInInitializerError} — catchable, but the class then stays broken for the rest
 * of the JVM), so the decode path, component deep-compare, registry-readiness retry, and
 * concurrent first-initialization are left to in-game QA (pending — not yet performed). What
 * <em>is</em> unit-testable, and is asserted below, is the fold structure itself: because
 * {@link CompiledFilter}'s node checks the ingredient type <em>before</em> any decode, a
 * non-{@code item} view exercises folding + short-circuit without ever decoding. That lets us
 * prove (a) a whole run collapses to a single node (O(1) type checks regardless of run size),
 * (b) a type mismatch never inspects the resource location or decodes, (c) exact-stack runs
 * segment Id runs into separate nodes, and (d) the folded nodes evaluate in the original
 * encounter order (recorded call sequence + short-circuit probes).
 */
class CompiledFilterExactStackFoldingTest {

	@Test
	void exactStackRunFoldsToSingleNodeWithConstantTypeChecksRegardlessOfRunSize() {
		CompiledFilter small = CompiledFilter.compile(buildExactStackRunAny(5));
		CompiledFilter large = CompiledFilter.compile(buildExactStackRunAny(1000));

		// A non-item view: the folded node's type gate returns false before any decode, so this is
		// safe to evaluate and reveals how many type checks the whole run costs.
		CountingIngredientView smallView = new CountingIngredientView("fluid", Identifier.parse("minecraft:water"));
		CountingIngredientView largeView = new CountingIngredientView("fluid", Identifier.parse("minecraft:water"));

		assertFalse(small.matches(smallView));
		assertFalse(large.matches(largeView));

		// One folded node -> exactly one ingredientType() consultation, independent of run size.
		assertEquals(1, smallView.ingredientTypeCalls);
		assertEquals(smallView.ingredientTypeCalls, largeView.ingredientTypeCalls,
			"ingredientType() call count must not scale with the exact-stack run size (i.e. the run must fold to one node)");
		// The type gate short-circuits before the resource-location lookup (and before any decode).
		assertEquals(0, smallView.resourceLocationCalls);
		assertEquals(0, largeView.resourceLocationCalls);
	}

	@Test
	void exactStackTypeMismatchNeverInspectsIdentifierOrDecodes() {
		CompiledFilter compiled = CompiledFilter.compile(buildExactStackRunAny(50));

		CountingIngredientView fluidView = new CountingIngredientView("fluid", Identifier.parse("minecraft:lava"));
		// No decode is attempted (a decode here would fail ItemStack's class initialization with an
		// ExceptionInInitializerError), so simply completing this call proves the type gate
		// precedes initialization.
		assertFalse(compiled.matches(fluidView));
		assertEquals(1, fluidView.ingredientTypeCalls);
		assertEquals(0, fluidView.resourceLocationCalls);
	}

	@Test
	void exactStackRunsSegmentIdRunsIntoSeparateNodesPreservingOrder() {
		// Encounter order: id-run, exact-stack-run, id-run. Ids fold into two IdSetNodes and the two
		// exact stacks fold into one ExactStackSetNode between them.
		GroupFilter tree = new GroupFilter.Any(List.of(
			Filters.id("item", "modns:a"),
			Filters.id("item", "modns:b"),
			Filters.exactStack("first"),
			Filters.exactStack("second"),
			Filters.id("item", "modns:c")
		));
		CompiledFilter compiled = CompiledFilter.compile(tree);

		// A non-item view drives every top-level child without decoding. Each IdSetNode consults
		// resourceLocation() then ingredientType(); the (single) ExactStackSetNode consults only
		// ingredientType() before its type gate returns false.
		CountingIngredientView view = new CountingIngredientView("fluid", Identifier.parse("modns:x"));
		assertFalse(compiled.matches(view));

		assertEquals(2, view.resourceLocationCalls, "expected exactly two IdSetNodes (the id runs on either side of the exact-stack run)");
		assertEquals(3, view.ingredientTypeCalls, "expected two IdSetNodes + one folded ExactStackSetNode (== 3), not four unfolded exact-stack nodes");
	}

	@Test
	void foldedNodesEvaluateInOriginalEncounterOrder() {
		// Tree (encounter order): tag A | id-run (folds to IdSetNode) | tag B | exact-stack-run
		// (folds to ExactStackSetNode) | tag C. Fluid-typed tag markers make every node's calls
		// observable on a fluid view: TagNode(fluid) -> "type" (sameType) then "tag:<id>";
		// IdSetNode -> "rl" then "type"; ExactStackSetNode -> "type" only (type gate, no decode).
		// Recording the full call sequence pins each folded node to its slot between the markers -
		// a reordering (e.g. hoisting either folded node to the front or back) would produce a
		// different sequence, which the node-count assertions of the other tests could not detect.
		GroupFilter tree = new GroupFilter.Any(List.of(
			Filters.tag("fluid", "marker:a"),
			Filters.id("item", "modns:ore_0"),
			Filters.id("item", "modns:ore_1"),
			Filters.tag("fluid", "marker:b"),
			Filters.exactStack("first"),
			Filters.exactStack("second"),
			Filters.tag("fluid", "marker:c")
		));
		CompiledFilter compiled = CompiledFilter.compile(tree);

		RecordingIngredientView missView = new RecordingIngredientView("fluid", Identifier.parse("modns:x"), null);
		assertFalse(compiled.matches(missView));
		assertEquals(
			List.of(
				"type", "tag:marker:a",   // TagNode A
				"rl", "type",             // IdSetNode (single folded node for the id run)
				"type", "tag:marker:b",   // TagNode B
				"type",                   // ExactStackSetNode (single folded node; type gate only)
				"type", "tag:marker:c"),  // TagNode C
			missView.calls,
			"folded nodes must evaluate in original encounter order with one node per run"
		);

		// Short-circuit probe: when marker B matches, evaluation must stop there - the folded
		// exact-stack node and tag C must never be consulted.
		RecordingIngredientView shortCircuitView = new RecordingIngredientView("fluid", Identifier.parse("modns:x"), Identifier.parse("marker:b"));
		assertTrue(compiled.matches(shortCircuitView));
		assertEquals(
			List.of(
				"type", "tag:marker:a",
				"rl", "type",
				"type", "tag:marker:b"),
			shortCircuitView.calls,
			"a hit on the marker before the exact-stack run must short-circuit: no further 'type' call from ExactStackSetNode, no tag:marker:c"
		);
	}

	private static GroupFilter buildExactStackRunAny(int count) {
		List<GroupFilter> children = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			children.add(Filters.exactStack("stack_" + i));
		}
		return new GroupFilter.Any(List.copyOf(children));
	}

	/** Records the exact sequence of view calls, making node evaluation order observable. */
	private static final class RecordingIngredientView implements IngredientView {
		private final String ingredientType;
		private final Identifier resourceLocation;
		private final Identifier matchingTag;
		private final List<String> calls = new ArrayList<>();

		RecordingIngredientView(String ingredientType, Identifier resourceLocation, Identifier matchingTag) {
			this.ingredientType = ingredientType;
			this.resourceLocation = resourceLocation;
			this.matchingTag = matchingTag;
		}

		@Override
		public String ingredientType() {
			calls.add("type");
			return ingredientType;
		}

		@Override
		public Identifier resourceLocation() {
			calls.add("rl");
			return resourceLocation;
		}

		@Override
		public boolean hasTag(Identifier tagId) {
			calls.add("tag:" + tagId);
			return tagId.equals(matchingTag);
		}

		@Override
		public boolean matchesExactStack(String encodedStack) {
			calls.add("exact:" + encodedStack);
			return false;
		}

		@Override
		public boolean matchesDecodedExactStack(ItemStack decoded) {
			calls.add("exactDecoded");
			return false;
		}
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

		@Override
		public boolean matchesDecodedExactStack(ItemStack decoded) {
			// Never reached in these tests (only non-item views are used), but a real decode would
			// have thrown before getting here anyway.
			return false;
		}
	}
}
