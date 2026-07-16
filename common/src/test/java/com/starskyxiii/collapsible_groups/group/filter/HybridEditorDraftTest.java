package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contents×Rules hybrid editing — decode preserves advanced subtrees, {@code toFilter}
 * recombines them as {@code Any(preserved…, flat…)}, and the flat-index-safe predicate is
 * decoupled from editability.
 */
class HybridEditorDraftTest {

	// ---------------------------------------------------------------------
	// decode: four branches
	// ---------------------------------------------------------------------

	@Test
	void decodeNullIsEmptyNonEditable() {
		GroupFilterEditorDraft.DecodeResult r = GroupFilterEditorDraft.decode(null);

		assertFalse(r.structurallyEditable());
		assertFalse(r.flatIndexSafe());
		assertTrue(r.draft().isEmpty());
		assertTrue(r.preservedSubtrees().isEmpty());
	}

	@Test
	void decodeRootSingleSupportedLeafIsFlat() {
		GroupFilterEditorDraft.DecodeResult r = GroupFilterEditorDraft.decode(Filters.itemId("minecraft:stone"));

		assertTrue(r.structurallyEditable());
		assertTrue(r.flatIndexSafe());
		assertEquals(List.of("minecraft:stone"), List.copyOf(r.draft().explicitItemSelectors()));
		assertTrue(r.preservedSubtrees().isEmpty());
	}

	@Test
	void decodeRootAnyAllSupportedIsFlatAndFlatIndexSafe() {
		GroupFilter filter = Filters.any(
			Filters.itemId("minecraft:stone"),
			Filters.itemTag("c:ingots"),
			Filters.fluidId("minecraft:water"));

		GroupFilterEditorDraft.DecodeResult r = GroupFilterEditorDraft.decode(filter);

		assertTrue(r.structurallyEditable());
		assertTrue(r.flatIndexSafe());
		assertTrue(r.preservedSubtrees().isEmpty());
		assertEquals(List.of("minecraft:stone"), List.copyOf(r.draft().explicitItemSelectors()));
		assertEquals(List.of("c:ingots"), r.draft().itemTags());
		assertEquals(List.of("minecraft:water"), r.draft().fluidIds());
	}

	@Test
	void decodeRootAnyWithAdvancedChildIsHybrid() {
		GroupFilter advanced = Filters.not(Filters.itemTag("c:ingots"));
		GroupFilter filter = Filters.any(Filters.itemId("minecraft:stone"), advanced);

		GroupFilterEditorDraft.DecodeResult r = GroupFilterEditorDraft.decode(filter);

		assertTrue(r.structurallyEditable());
		assertFalse(r.flatIndexSafe());
		assertEquals(List.of("minecraft:stone"), List.copyOf(r.draft().explicitItemSelectors()));
		assertEquals(List.of(advanced), r.preservedSubtrees());
	}

	@Test
	void decodeRootNonAnyAdvancedIsWholePreservedEmptyDraft() {
		GroupFilter filter = Filters.blockTag("minecraft:logs");

		GroupFilterEditorDraft.DecodeResult r = GroupFilterEditorDraft.decode(filter);

		assertTrue(r.structurallyEditable());
		assertFalse(r.flatIndexSafe());
		assertTrue(r.draft().isEmpty());
		assertEquals(List.of(filter), r.preservedSubtrees());
	}

	// ---------------------------------------------------------------------
	// Round-trip: untouched trees are structurally equivalent
	// ---------------------------------------------------------------------

	@Test
	void roundTripNonAnyRootIsNotWrappedInAny() {
		// The identity guarantee: a rules-only, non-Any root passes through the contents
		// editor untouched with no superfluous Any wrap.
		GroupFilter original = Filters.not(Filters.itemTag("c:ingots"));

		GroupFilter out = GroupFilterEditorDraft.decode(original).draft().toFilter().orElseThrow();

		assertEquals(original, out);
	}

	@Test
	void roundTripAllRootIsNotWrappedInAny() {
		GroupFilter original = new GroupFilter.All(List.of(
			Filters.itemTag("c:ingots"), Filters.not(Filters.itemId("minecraft:stone"))));

		GroupFilter out = GroupFilterEditorDraft.decode(original).draft().toFilter().orElseThrow();

		assertEquals(original, out);
	}

	@Test
	void roundTripHybridAnyIsPreservedFirstAndStructurallyEquivalent() {
		GroupFilter advanced = Filters.not(Filters.itemTag("c:ingots"));
		GroupFilter original = Filters.any(Filters.itemId("minecraft:stone"), advanced);

		GroupFilter out = GroupFilterEditorDraft.decode(original).draft().toFilter().orElseThrow();

		// Stable order: preserved advanced subtree first, then the flat leaf.
		GroupFilter.Any any = assertInstanceOf(GroupFilter.Any.class, out);
		assertEquals(List.of(advanced, Filters.itemId("minecraft:stone")), any.children());
	}

	@Test
	void roundTripMultiplePreservedSubtreesKeepsAllOfThem() {
		GroupFilter adv1 = Filters.not(Filters.itemTag("c:ingots"));
		GroupFilter adv2 = Filters.blockTag("minecraft:logs");
		GroupFilter original = Filters.any(adv1, adv2, Filters.itemId("minecraft:stone"));

		GroupFilterEditorDraft.DecodeResult r = GroupFilterEditorDraft.decode(original);
		assertEquals(List.of(adv1, adv2), r.preservedSubtrees());

		GroupFilter.Any out = assertInstanceOf(GroupFilter.Any.class, r.draft().toFilter().orElseThrow());
		assertEquals(List.of(adv1, adv2, Filters.itemId("minecraft:stone")), out.children());
	}

	// ---------------------------------------------------------------------
	// After editing
	// ---------------------------------------------------------------------

	@Test
	void editingNonAnyRootWrapsOriginalTreeInAny() {
		GroupFilter original = Filters.blockTag("minecraft:logs");
		GroupFilterEditorDraft live = copyOfDecoded(original);

		// User clicks a contents cell, adding one explicit item.
		live.explicitItemSelectors().add("minecraft:stone");

		GroupFilter.Any out = assertInstanceOf(GroupFilter.Any.class, live.toFilter().orElseThrow());
		assertEquals(List.of(original, Filters.itemId("minecraft:stone")), out.children());
	}

	@Test
	void editingHybridDraftLeavesPreservedSubtreeUnchanged() {
		GroupFilter advanced = Filters.not(Filters.itemTag("c:ingots"));
		GroupFilter original = Filters.any(Filters.itemId("minecraft:stone"), advanced);
		GroupFilterEditorDraft live = copyOfDecoded(original);

		// Add one item, remove the pre-existing one — the preserved Not never moves.
		live.explicitItemSelectors().add("minecraft:diamond");
		live.explicitItemSelectors().remove("minecraft:stone");

		GroupFilter.Any out = assertInstanceOf(GroupFilter.Any.class, live.toFilter().orElseThrow());
		assertEquals(List.of(advanced, Filters.itemId("minecraft:diamond")), out.children());
	}

	@Test
	void removingLastFlatLeafFromHybridReturnsBarePreservedSubtree() {
		GroupFilter advanced = Filters.not(Filters.itemTag("c:ingots"));
		GroupFilter original = Filters.any(Filters.itemId("minecraft:stone"), advanced);
		GroupFilterEditorDraft live = copyOfDecoded(original);

		live.explicitItemSelectors().remove("minecraft:stone");

		// Only the lone preserved subtree remains — no Any wrap.
		assertEquals(advanced, live.toFilter().orElseThrow());
	}

	// ---------------------------------------------------------------------
	// toFilter special cases
	// ---------------------------------------------------------------------

	@Test
	void toFilterEmptyDraftEmptyPreservedIsEmpty() {
		assertTrue(GroupFilterEditorDraft.empty().toFilter().isEmpty());
	}

	@Test
	void toFilterSinglePreservedNoFlatReturnsSubtreeDirectly() {
		GroupFilter advanced = Filters.blockTag("minecraft:logs");
		GroupFilterEditorDraft draft = GroupFilterEditorDraft.empty();
		draft.preservedSubtrees().add(advanced);

		assertEquals(advanced, draft.toFilter().orElseThrow());
	}

	@Test
	void toFilterSingleFlatNoPreservedReturnsLeafDirectly() {
		GroupFilterEditorDraft draft = GroupFilterEditorDraft.empty();
		draft.explicitItemSelectors().add("minecraft:stone");

		assertEquals(Filters.itemId("minecraft:stone"), draft.toFilter().orElseThrow());
	}

	// ---------------------------------------------------------------------
	// flat-index-safe predicate truth table
	// ---------------------------------------------------------------------

	@Test
	void flatIndexSafeTruthTable() {
		// null: non-editable, not safe
		assertFalse(GroupFilterEditorDraft.decode(null).flatIndexSafe());
		// single supported leaf: editable + safe
		assertTrue(GroupFilterEditorDraft.decode(Filters.itemId("minecraft:stone")).flatIndexSafe());
		// Any of only supported leaves: editable + safe
		assertTrue(GroupFilterEditorDraft.decode(
			Filters.any(Filters.itemId("minecraft:stone"), Filters.itemTag("c:ingots"))).flatIndexSafe());
		// Any with an advanced child: editable but NOT safe
		assertFalse(GroupFilterEditorDraft.decode(
			Filters.any(Filters.itemId("minecraft:stone"), Filters.not(Filters.itemTag("c:ingots")))).flatIndexSafe());
		// non-Any advanced root: editable but NOT safe
		assertFalse(GroupFilterEditorDraft.decode(Filters.blockTag("minecraft:logs")).flatIndexSafe());
	}

	/**
	 * Mirrors the loader flow: decode, then copy the decoded flat draft and preserved
	 * subtrees into a fresh live draft that the contents editor mutates in place.
	 */
	private static GroupFilterEditorDraft copyOfDecoded(GroupFilter filter) {
		GroupFilterEditorDraft.DecodeResult decoded = GroupFilterEditorDraft.decode(filter);
		GroupFilterEditorDraft live = GroupFilterEditorDraft.empty();
		live.explicitItemSelectors().addAll(decoded.draft().explicitItemSelectors());
		live.itemTags().addAll(decoded.draft().itemTags());
		live.fluidIds().addAll(decoded.draft().fluidIds());
		live.fluidTags().addAll(decoded.draft().fluidTags());
		live.genericIds().addAll(decoded.draft().genericIds());
		live.genericTags().addAll(decoded.draft().genericTags());
		live.preservedSubtrees().addAll(decoded.preservedSubtrees());
		return live;
	}
}
