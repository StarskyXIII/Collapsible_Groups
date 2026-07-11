package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link HybridPreviewUnion#orderedUnion} must reproduce, item-for-item and
 * order-for-order, what a single full scan of the candidate list would produce for
 * {@code flat OR preserved}. Modelled here with distinct String instances standing in for the
 * JEI ItemStacks and an identity ordinal map standing in for {@code EditorItemIndex.orderByIdentity}.
 */
class HybridPreviewUnionTest {

	/** Candidate list of 10 distinct (by identity) tokens in "JEI order". */
	private static List<String> candidates() {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			out.add(new String("c" + i)); // NOSONAR — deliberate distinct identity per token
		}
		return out;
	}

	private static ToIntFunction<String> ordinalOf(List<String> candidates) {
		IdentityHashMap<String, Integer> ord = new IdentityHashMap<>();
		for (int i = 0; i < candidates.size(); i++) ord.put(candidates.get(i), i);
		return s -> ord.getOrDefault(s, Integer.MAX_VALUE);
	}

	/** Reference full scan: the candidate order, filtered to identity-membership of either subset. */
	private static List<String> fullScan(List<String> candidates, List<String> flat, List<String> preserved) {
		Map<String, Boolean> in = new IdentityHashMap<>();
		for (String s : flat) in.put(s, Boolean.TRUE);
		for (String s : preserved) in.put(s, Boolean.TRUE);
		List<String> out = new ArrayList<>();
		for (String c : candidates) if (in.containsKey(c)) out.add(c);
		return out;
	}

	@Test
	void unionEqualsFullScanOrderWithOverlap() {
		List<String> candidates = candidates();
		ToIntFunction<String> ordinal = ordinalOf(candidates);

		// flat = indices {1,4,7} in order; preserved = {2,4,8} — overlaps at 4 by reference.
		List<String> flat = List.of(candidates.get(1), candidates.get(4), candidates.get(7));
		List<String> preserved = List.of(candidates.get(2), candidates.get(4), candidates.get(8));

		List<String> union = HybridPreviewUnion.orderedUnion(flat, preserved, ordinal);

		assertEquals(fullScan(candidates, flat, preserved), union,
			"union must equal the full-scan candidate-order sublist");
		// Order-for-order: indices 1,2,4,7,8; the shared token 4 appears exactly once.
		assertEquals(List.of("c1", "c2", "c4", "c7", "c8"), union);
	}

	@Test
	void unionDedupesByIdentityNotValue() {
		List<String> candidates = candidates();
		ToIntFunction<String> ordinal = ordinalOf(candidates);
		List<String> flat = List.of(candidates.get(3));
		List<String> preserved = List.of(candidates.get(3), candidates.get(5));

		List<String> union = HybridPreviewUnion.orderedUnion(flat, preserved, ordinal);
		assertEquals(2, union.size(), "the shared identity must be deduplicated");
		assertEquals(List.of("c3", "c5"), union);
	}

	@Test
	void preservedBeforeFlatInCandidateOrderStillSortsCorrectly() {
		List<String> candidates = candidates();
		ToIntFunction<String> ordinal = ordinalOf(candidates);
		// preserved matches earlier candidates than flat — result must still be candidate order.
		List<String> flat = List.of(candidates.get(9));
		List<String> preserved = List.of(candidates.get(0), candidates.get(6));

		List<String> union = HybridPreviewUnion.orderedUnion(flat, preserved, ordinal);
		assertEquals(List.of("c0", "c6", "c9"), union);
		assertEquals(fullScan(candidates, flat, preserved), union);
	}

	@Test
	void emptyPreservedReturnsFlatCopy() {
		List<String> flat = List.of("a", "b");
		List<String> union = HybridPreviewUnion.orderedUnion(flat, List.of(), s -> 0);
		assertEquals(flat, union);
	}

	@Test
	void emptyFlatReturnsPreservedCopy() {
		List<String> candidates = candidates();
		ToIntFunction<String> ordinal = ordinalOf(candidates);
		List<String> preserved = List.of(candidates.get(1), candidates.get(3));
		List<String> union = HybridPreviewUnion.orderedUnion(List.of(), preserved, ordinal);
		assertEquals(preserved, union);
	}

	@Test
	void coverageIdsMatchFullScan() {
		// coverage equivalence corollary: the union's identity membership set == the full scan's.
		List<String> candidates = candidates();
		ToIntFunction<String> ordinal = ordinalOf(candidates);
		List<String> flat = List.of(candidates.get(0), candidates.get(5));
		List<String> preserved = List.of(candidates.get(5), candidates.get(9));

		List<String> union = HybridPreviewUnion.orderedUnion(flat, preserved, ordinal);
		List<String> scan = fullScan(candidates, flat, preserved);
		assertEquals(scan, union);
		// same references, same order — coverage keys derived from either are identical.
		for (int i = 0; i < union.size(); i++) assertSame(scan.get(i), union.get(i));
	}
}
