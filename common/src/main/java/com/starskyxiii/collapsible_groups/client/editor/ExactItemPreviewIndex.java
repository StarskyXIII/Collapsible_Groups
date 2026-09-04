package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class ExactItemPreviewIndex {
	static final int MAX_ENTRIES = 8192;
	static final long MAX_BYTES = 32L * 1024 * 1024;
	private final List<ItemStack> items;
	private final List<ItemStack> snapshots;
	private final Map<Integer, List<Integer>> buckets = new HashMap<>();
	private final LinkedHashMap<String, int[]> selectors = new LinkedHashMap<>(16, 0.75f, true);
	private final ToIntFunction<ItemStack> hash;
	private Object registryIdentity;
	private long retainedBytes;
	private long decodes;
	private long comparisons;
	private long cacheHits;

	public ExactItemPreviewIndex(List<ItemStack> items) {
		this(items, ItemStack::hashItemAndComponents);
	}

	ExactItemPreviewIndex(List<ItemStack> items, ToIntFunction<ItemStack> hash) {
		this.items = List.copyOf(items);
		this.hash = hash;
		this.snapshots = items.stream().map(GroupItemSelector::normalizedCopy).toList();
		for (int i = 0; i < snapshots.size(); i++) {
			buckets.computeIfAbsent(hash.applyAsInt(snapshots.get(i)), ignored -> new ArrayList<>()).add(i);
		}
	}

	public List<ItemStack> resolve(GroupFilter filter, GroupItemSelector.ExactDecodeContext context) {
		if (registryIdentity != context.registryIdentity()) {
			selectors.clear();
			retainedBytes = 0;
			registryIdentity = context.registryIdentity();
		}
		BitSet matches = evaluate(filter, context).matches();
		return matches.stream().mapToObj(items::get).toList();
	}

	private Result evaluate(GroupFilter filter, GroupItemSelector.ExactDecodeContext context) {
		if (filter instanceof GroupFilter.ExactStack exact) {
			BitSet matches = new BitSet();
			for (int ordinal : exactMatches(exact.encodedStack(), context)) matches.set(ordinal);
			return new Result(matches, new BitSet());
		}
		if (filter instanceof GroupFilter.Not not) {
			Result child = evaluate(not.child(), context);
			BitSet matches = all();
			matches.andNot(child.matches());
			matches.andNot(child.unavailable());
			return new Result(matches, child.unavailable());
		}
		if (filter instanceof GroupFilter.Any || filter instanceof GroupFilter.All) {
			boolean any = filter instanceof GroupFilter.Any;
			List<GroupFilter> children = any ? ((GroupFilter.Any) filter).children() : ((GroupFilter.All) filter).children();
			BitSet matches = any ? new BitSet() : all();
			BitSet possible = any ? new BitSet() : all();
			for (GroupFilter child : children) {
				Result result = evaluate(child, context);
				BitSet childPossible = (BitSet) result.matches().clone();
				childPossible.or(result.unavailable());
				if (any) {
					matches.or(result.matches());
					possible.or(childPossible);
				} else {
					matches.and(result.matches());
					possible.and(childPossible);
				}
			}
			possible.andNot(matches);
			return new Result(matches, possible);
		}
		CompiledFilter compiled = CompiledFilter.compile(filter);
		BitSet matches = new BitSet();
		BitSet unavailable = new BitSet();
		for (int i = 0; i < snapshots.size(); i++) {
			switch (compiled.evaluate(new ItemStackIngredientView(snapshots.get(i)))) {
				case MATCH -> matches.set(i);
				case UNAVAILABLE -> unavailable.set(i);
				case NO_MATCH -> { }
			}
		}
		return new Result(matches, unavailable);
	}

	private int[] exactMatches(String selector, GroupItemSelector.ExactDecodeContext context) {
		int[] cached = selectors.get(selector);
		if (cached != null) {
			cacheHits++;
			return cached;
		}
		decodes++;
		var decoded = GroupItemSelector.decodeExactSelector("stack:" + selector, context);
		List<Integer> matches = new ArrayList<>();
		if (decoded.isPresent()) {
			ItemStack stack = decoded.get();
			for (int ordinal : buckets.getOrDefault(hash.applyAsInt(stack), List.of())) {
				comparisons++;
				if (ItemStack.isSameItemSameComponents(stack, snapshots.get(ordinal))) matches.add(ordinal);
			}
		}
		int[] result = matches.stream().mapToInt(Integer::intValue).toArray();
		if (decoded.isPresent() || context.liveRegistry()) cache(selector, result);
		return result;
	}

	private void cache(String selector, int[] ordinals) {
		long cost = cost(selector, ordinals);
		if (cost > MAX_BYTES) return;
		while (!selectors.isEmpty() && (selectors.size() >= MAX_ENTRIES || retainedBytes + cost > MAX_BYTES)) {
			var oldest = selectors.pollFirstEntry();
			retainedBytes -= cost(oldest.getKey(), oldest.getValue());
		}
		selectors.put(selector, ordinals);
		retainedBytes += cost;
	}

	private static long cost(String selector, int[] ordinals) {
		return 160L + 2L * selector.length() + 4L * ordinals.length;
	}

	private BitSet all() {
		BitSet all = new BitSet();
		all.set(0, items.size());
		return all;
	}

	long decodes() { return decodes; }
	long comparisons() { return comparisons; }
	long cacheHits() { return cacheHits; }
	int cachedSelectors() { return selectors.size(); }
	long retainedBytes() { return retainedBytes; }
	private record Result(BitSet matches, BitSet unavailable) {}
}
