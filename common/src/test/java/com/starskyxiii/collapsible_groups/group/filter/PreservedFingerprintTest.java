package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The preserved-subtree fingerprint is {@code List.copyOf(preservedSubtrees)}
 * compared by GroupFilter record value equality — no string serialization. These are the
 * "lifeline" tests that guard the hybrid preview cache's correctness:
 *
 * <ul>
 * <li>{@code decode → toFilter → decode} yields a value-equal preserved list (so the
 * fingerprint is stable across a no-op contents pass);</li>
 * <li>a flat-only toggle (adding/removing a contents leaf) leaves the preserved fingerprint
 * unchanged (so a click/removal does not miss the cache);</li>
 * <li>Not / All / ItemPathContains preserved samples all round-trip.</li>
 * </ul>
 */
class PreservedFingerprintTest {

	private static List<GroupFilter> preservedOf(GroupFilter filter) {
		return GroupFilterEditorDraft.decode(filter).preservedSubtrees();
	}

	private static GroupFilter roundTrip(GroupFilter filter) {
		return GroupFilterEditorDraft.decode(filter).draft().toFilter().orElseThrow();
	}

	// ------------------------------------------------------------------
	// decode → toFilter → decode fingerprint stability, three sample kinds
	// ------------------------------------------------------------------

	@Test
	void notSamplePreservedRoundTrips() {
		GroupFilter hybrid = Filters.any(
			Filters.not(Filters.itemId("minecraft:stone")),
			Filters.itemId("minecraft:iron_ingot"),
			Filters.itemTag("c:ingots"));

		List<GroupFilter> before = preservedOf(hybrid);
		List<GroupFilter> after = preservedOf(roundTrip(hybrid));

		assertFalse(before.isEmpty(), "Not subtree must be preserved");
		assertEquals(before, after, "preserved fingerprint must survive decode→toFilter→decode");
	}

	@Test
	void allSamplePreservedRoundTrips() {
		GroupFilter hybrid = Filters.any(
			Filters.all(Filters.itemTag("c:ingots"), Filters.itemId("minecraft:iron_ingot")),
			Filters.itemTag("c:nuggets"));

		List<GroupFilter> before = preservedOf(hybrid);
		List<GroupFilter> after = preservedOf(roundTrip(hybrid));

		assertFalse(before.isEmpty(), "All subtree must be preserved");
		assertEquals(before, after);
	}

	@Test
	void itemPathContainsSamplePreservedRoundTrips() {
		GroupFilter hybrid = Filters.any(
			Filters.itemPathContains("ore"),
			Filters.itemId("minecraft:iron_ingot"));

		List<GroupFilter> before = preservedOf(hybrid);
		List<GroupFilter> after = preservedOf(roundTrip(hybrid));

		assertFalse(before.isEmpty(), "ItemPathContains subtree must be preserved");
		assertEquals(before, after);
	}

	// ------------------------------------------------------------------
	// A flat contents toggle does not perturb the preserved fingerprint
	// ------------------------------------------------------------------

	@Test
	void flatToggleLeavesPreservedFingerprintUnchanged() {
		GroupFilter hybrid = Filters.any(
			Filters.not(Filters.itemId("minecraft:stone")),
			Filters.itemPathContains("ore"),
			Filters.itemId("minecraft:iron_ingot"));

		GroupFilterEditorDraft.DecodeResult decoded = GroupFilterEditorDraft.decode(hybrid);
		List<GroupFilter> baseline = List.copyOf(decoded.preservedSubtrees());

		// Simulate a contents-grid add of a whole item into the flat draft.
		decoded.draft().explicitItemSelectors().add("minecraft:gold_ingot");
		List<GroupFilter> afterAdd = preservedOf(decoded.draft().toFilter().orElseThrow());
		assertEquals(baseline, afterAdd, "adding a flat item must not change the preserved fingerprint");

		// Simulate a removal of that same item.
		decoded.draft().explicitItemSelectors().remove("minecraft:gold_ingot");
		List<GroupFilter> afterRemove = preservedOf(decoded.draft().toFilter().orElseThrow());
		assertEquals(baseline, afterRemove, "removing a flat item must not change the preserved fingerprint");
	}

	// ------------------------------------------------------------------
	// The hybrid predicate that routes rebuild() into the union / full-scan
	// branch (and populateFullMatchCacheFromSaved into its full-scan branch).
	// ------------------------------------------------------------------

	@Test
	void hybridDraftIsEditableButNotFlatIndexSafe() {
		GroupFilter hybrid = Filters.any(
			Filters.not(Filters.itemId("minecraft:stone")),
			Filters.itemId("minecraft:iron_ingot"));

		GroupFilterEditorDraft.DecodeResult decoded = GroupFilterEditorDraft.decode(hybrid);
		assertTrue(decoded.structurallyEditable(), "hybrid draft is still contents-editable");
		assertFalse(decoded.flatIndexSafe(), "hybrid draft must NOT be flat-index safe (routes to full scan / union)");
		assertFalse(decoded.preservedSubtrees().isEmpty());
	}

	@Test
	void pureFlatDraftIsFlatIndexSafe() {
		GroupFilter flat = Filters.any(
			Filters.itemId("minecraft:iron_ingot"),
			Filters.itemTag("c:ingots"));

		GroupFilterEditorDraft.DecodeResult decoded = GroupFilterEditorDraft.decode(flat);
		assertTrue(decoded.flatIndexSafe(), "a pure flat draft stays on the fast indexed path");
		assertTrue(decoded.preservedSubtrees().isEmpty());
	}
}
