package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

final class EditorItemSelectionHelper {
    private final IdentityHashMap<ItemStack, EncodedSelection> exactSelectorCache = new IdentityHashMap<>();
    private final Map<String, Optional<ItemStack>> decodedSelections = new HashMap<>();
    private final ToIntFunction<ItemStack> hash;
    private Object registryIdentity;
    private Set<String> indexedSelections;
    private final Map<Integer, List<ItemStack>> selectionBuckets = new HashMap<>();

    EditorItemSelectionHelper() {
        this(ItemStack::hashItemAndComponents);
    }

    EditorItemSelectionHelper(ToIntFunction<ItemStack> hash) {
        this.hash = hash;
    }

	Optional<String> cachedExactSelector(ItemStack stack) {
        refreshRegistry(GroupItemSelector.exactDecodeContext());
        EncodedSelection cached = exactSelectorCache.get(stack);
        if (cached != null && ItemStack.isSameItemSameComponents(cached.snapshot(), stack)) {
            return Optional.of(cached.selector());
        }
        exactSelectorCache.remove(stack);
        ItemStack snapshot = GroupItemSelector.normalizedCopy(stack);
        Optional<String> encoded = GroupItemSelector.tryExactSelector(snapshot);
        encoded.ifPresent(selector -> exactSelectorCache.put(stack, new EncodedSelection(snapshot, selector)));
        return encoded;
	}

	void clearCache() { exactSelectorCache.clear(); decodedSelections.clear(); selectionChanged(); }

    void selectionChanged() {
        indexedSelections = null;
        selectionBuckets.clear();
    }

    private void indexSelections(Set<String> explicitSet) {
        var context = GroupItemSelector.exactDecodeContext();
        refreshRegistry(context);
        if (indexedSelections == explicitSet) return;
        selectionBuckets.clear();
        boolean retry = false;
        for (String selector : explicitSet) {
            if (!GroupItemSelector.isExactSelector(selector)) continue;
            Optional<ItemStack> decoded = decode(selector, context);
            decoded.ifPresent(this::indexSelection);
            retry |= decoded.isEmpty() && !context.liveRegistry();
        }
        indexedSelections = retry ? null : explicitSet;
    }

    private void refreshRegistry(GroupItemSelector.ExactDecodeContext context) {
        Object current = context.registryIdentity();
        if (registryIdentity != current) {
            clearCache();
            registryIdentity = current;
        }
    }

    private Optional<ItemStack> decode(String selector, GroupItemSelector.ExactDecodeContext context) {
        Optional<ItemStack> cached = decodedSelections.get(selector);
        if (cached != null) return cached;
        Optional<ItemStack> decoded = GroupItemSelector.decodeExactSelector(selector, context);
        if (decoded.isPresent() || context.liveRegistry()) decodedSelections.put(selector, decoded);
        return decoded;
    }

    private void indexSelection(ItemStack stack) {
        selectionBuckets.computeIfAbsent(hash.applyAsInt(stack), ignored -> new ArrayList<>()).add(stack);
    }

    private boolean containsIndexed(ItemStack stack) {
        for (ItemStack selected : selectionBuckets.getOrDefault(hash.applyAsInt(stack), List.of())) {
            if (ItemStack.isSameItemSameComponents(selected, stack)) return true;
        }
        return false;
    }

    private boolean removeEquivalent(ItemStack stack, Set<String> explicitSet) {
        selectionChanged();
        var context = GroupItemSelector.exactDecodeContext();
        refreshRegistry(context);
        return explicitSet.removeIf(selector -> GroupItemSelector.isExactSelector(selector)
            && decode(selector, context).map(decoded -> ItemStack.isSameItemSameComponents(decoded, stack)).orElse(false));
    }

	boolean isWholeItemSelected(ItemStack stack, Set<String> explicitSet) {
		return explicitSet.contains(GroupItemSelector.wholeItemSelector(stack));
	}

	boolean isExactSelected(ItemStack stack, Set<String> explicitSet) {
		if (cachedExactSelector(stack).map(explicitSet::contains).orElse(false)) return true;
        indexSelections(explicitSet);
        return containsIndexed(stack);
	}

	/**
	 * the selector stored for a plain single-click. Component-less items store the cheap,
	 * broad whole-item id (so common vanilla items never bloat into exact-stack rules); items that
	 * carry a component patch keep the exact-stack selector. Plain-clicking therefore toggles the
	 * whole-item selection on/off for component-less items, while items with components use exact
	 * selectors.
	 */
	private String preferredSelector(ItemStack stack) {
		return stack.getComponentsPatch().isEmpty()
			? GroupItemSelector.wholeItemSelector(stack)
			: GroupItemSelector.exactSelector(stack);
	}

	boolean hasPreferredSelection(ItemStack stack, Set<String> explicitSet) {
		String selector = preferredSelector(stack);
        return explicitSet.contains(selector) || GroupItemSelector.isExactSelector(selector) && isExactSelected(stack, explicitSet);
	}

	void toggleSingleSelection(ItemStack stack, Set<String> explicitSet) {
        selectionChanged();
		String preferredSelector = preferredSelector(stack);
		if (GroupItemSelector.isExactSelector(preferredSelector)
            ? removeEquivalent(stack, explicitSet) : explicitSet.remove(preferredSelector)) {
			return;
		}
		if (GroupItemSelector.isExactSelector(preferredSelector)) {
			explicitSet.remove(GroupItemSelector.wholeItemSelector(stack));
		} else {
			removeExactSelectionsForItem(stack, explicitSet);
		}
		explicitSet.add(preferredSelector);
	}

	boolean addSingleSelectionIfAbsent(ItemStack stack, Set<String> explicitSet) {
        selectionChanged();
		String preferredSelector = preferredSelector(stack);
		if (hasPreferredSelection(stack, explicitSet)) {
			return false;
		}
		boolean changed;
		if (GroupItemSelector.isExactSelector(preferredSelector)) {
			changed = explicitSet.remove(GroupItemSelector.wholeItemSelector(stack));
		} else {
			changed = removeExactSelectionsForItem(stack, explicitSet);
		}
		changed |= explicitSet.add(preferredSelector);
		return changed;
	}

	void toggleWholeItemSelection(ItemStack stack, Set<String> explicitSet) {
        selectionChanged();
		String wholeItemSelector = GroupItemSelector.wholeItemSelector(stack);
		if (explicitSet.remove(wholeItemSelector)) {
			return;
		}
		removeExactSelectionsForItem(stack, explicitSet);
		explicitSet.add(wholeItemSelector);
	}

	void removeSingleSelection(ItemStack stack, List<ItemStack> allItems, Set<String> explicitSet) {
		if (removeEquivalent(stack, explicitSet)) {
			return;
		}
		String wholeItemSelector = GroupItemSelector.wholeItemSelector(stack);
		if (explicitSet.remove(wholeItemSelector)) {
			addAllSiblingVariantsExcept(stack, allItems, explicitSet);
		}
	}

	void removeAllSelectionsForItem(ItemStack stack, Set<String> explicitSet) {
        selectionChanged();
		Set<String> selectors = explicitSet.stream()
			.filter(selector -> GroupItemSelector.isSelectorForSameItem(selector, stack))
			.collect(Collectors.toSet());
		explicitSet.removeAll(selectors);
	}

	private boolean removeExactSelectionsForItem(ItemStack stack, Set<String> explicitSet) {
        selectionChanged();
		Set<String> selectors = explicitSet.stream()
			.filter(GroupItemSelector::isExactSelector)
			.filter(selector -> GroupItemSelector.isSelectorForSameItem(selector, stack))
			.collect(Collectors.toSet());
		return explicitSet.removeAll(selectors);
	}

	private void addAllSiblingVariantsExcept(ItemStack excludedStack, List<ItemStack> allItems,
		Set<String> explicitSet) {
        indexSelections(explicitSet);
        try {
            for (ItemStack candidate : allItems) {
                if (!GroupItemSelector.sameItem(candidate, excludedStack)
                    || ItemStack.isSameItemSameComponents(candidate, excludedStack) || containsIndexed(candidate)) continue;
                cachedExactSelector(candidate).ifPresent(selector -> {
                    if (explicitSet.add(selector)) indexSelection(GroupItemSelector.normalizedCopy(candidate));
                });
            }
        } finally {
            selectionChanged();
        }
	}

    private record EncodedSelection(ItemStack snapshot, String selector) {}
}
