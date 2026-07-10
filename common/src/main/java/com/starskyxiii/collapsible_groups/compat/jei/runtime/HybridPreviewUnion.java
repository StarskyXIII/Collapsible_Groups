package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * pure, Minecraft-free merge used by {@link EditorItemIndex} to union the flat
 * (indexed) preview subset with the preserved-subtree (full-scan) subset of a hybrid draft.
 *
 * <p>Both inputs are subsets of the same underlying ordered candidate list (the JEI item
 * cache), so their elements share object identity where they overlap. The union therefore
 * deduplicates by <em>identity</em> ({@link IdentityHashMap}-backed set) and re-sorts by the
 * caller-supplied stable ordinal, reproducing exactly the order a single full scan
 * ({@code candidates.stream().filter(flat OR preserved)}) would have produced.
 *
 * <p>Kept in its own class (not inlined into the MC-touching {@link EditorItemIndex}) so the
 * order/dedup equivalence can be unit-tested without a Minecraft bootstrap.
 */
final class HybridPreviewUnion {

	private HybridPreviewUnion() {}

	/**
	 * Ordinal-sorted, identity-deduplicated union of {@code flat} and {@code preserved}.
	 *
	 * @param flat      flat-index matches, already in ordinal order
	 * @param preserved preserved-subtree matches, already in ordinal order
	 * @param ordinal   stable ordinal of an element in the underlying candidate order;
	 *                  elements not found should map to a sentinel (e.g. {@code MAX_VALUE})
	 * @return a new immutable list = {@code flat ∪ preserved}, sorted by {@code ordinal}
	 */
	static <T> List<T> orderedUnion(List<T> flat, List<T> preserved, ToIntFunction<T> ordinal) {
		if (preserved.isEmpty()) {
			return List.copyOf(flat);
		}
		if (flat.isEmpty()) {
			return List.copyOf(preserved);
		}
		Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>(
			(flat.size() + preserved.size()) * 2));
		List<T> merged = new ArrayList<>(flat.size() + preserved.size());
		for (T t : flat) {
			if (seen.add(t)) merged.add(t);
		}
		for (T t : preserved) {
			if (seen.add(t)) merged.add(t);
		}
		merged.sort((a, b) -> Integer.compare(ordinal.applyAsInt(a), ordinal.applyAsInt(b)));
		return List.copyOf(merged);
	}
}
