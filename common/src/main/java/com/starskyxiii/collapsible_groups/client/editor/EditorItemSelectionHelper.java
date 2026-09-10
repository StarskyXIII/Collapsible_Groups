package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class EditorItemSelectionHelper {
	private final IdentityHashMap<ItemStack, Optional<String>> exactSelectorCache = new IdentityHashMap<>();
    private final java.util.Map<String, Optional<ItemStack>> decodedSelections = new java.util.HashMap<>();
    private Object registryIdentity;
    private Set<String> indexedSelections;
    private int indexedSize = -1;
    private final java.util.Map<Integer, List<ItemStack>> selectionBuckets = new java.util.HashMap<>();

	Optional<String> cachedExactSelector(ItemStack stack) {
		refreshRegistry();
		return exactSelectorCache.computeIfAbsent(stack, GroupItemSelector::tryExactSelector);
	}

	void clearCache() { exactSelectorCache.clear(); decodedSelections.clear(); selectionChanged(); }

    void selectionChanged() {
        indexedSelections = null;
        indexedSize = -1;
        selectionBuckets.clear();
    }

    private void indexSelections(Set<String> explicitSet) {
        refreshRegistry();
        if (indexedSelections == explicitSet && indexedSize == explicitSet.size()) return;
        selectionBuckets.clear();
        for (String selector : explicitSet) {
            if (!GroupItemSelector.isExactSelector(selector)) continue;
            decodedSelections.computeIfAbsent(selector, GroupItemSelector::decodeExactSelector).ifPresent(decoded ->
                selectionBuckets.computeIfAbsent(ItemStack.hashItemAndComponents(decoded), ignored -> new java.util.ArrayList<>()).add(decoded));
        }
        indexedSelections = explicitSet;
        indexedSize = explicitSet.size();
    }

    private void refreshRegistry() {
        Object current = GroupItemSelector.registryIdentity();
        if (registryIdentity != current) {
            clearCache();
            registryIdentity = current;
        }
    }

    private boolean equivalent(String selector, ItemStack stack) {
        refreshRegistry();
        if (!GroupItemSelector.isExactSelector(selector)) return false;
        return decodedSelections.computeIfAbsent(selector, GroupItemSelector::decodeExactSelector)
            .map(decoded -> ItemStack.isSameItemSameComponents(decoded, stack)).orElse(false);
    }

    private boolean removeEquivalent(ItemStack stack, Set<String> explicitSet) {
        selectionChanged();
        return explicitSet.removeIf(selector -> equivalent(selector, stack));
    }

	boolean isWholeItemSelected(ItemStack stack, Set<String> explicitSet) {
		return explicitSet.contains(GroupItemSelector.wholeItemSelector(stack));
	}

	boolean isExactSelected(ItemStack stack, Set<String> explicitSet) {
		if (cachedExactSelector(stack).map(explicitSet::contains).orElse(false)) return true;
        indexSelections(explicitSet);
        for (ItemStack selected : selectionBuckets.getOrDefault(ItemStack.hashItemAndComponents(stack), List.of())) {
            if (ItemStack.isSameItemSameComponents(selected, stack)) return true;
        }
        return false;
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
		Set<String> selectors = explicitSet.stream()
			.filter(GroupItemSelector::isExactSelector)
			.filter(selector -> GroupItemSelector.isSelectorForSameItem(selector, stack))
			.collect(Collectors.toSet());
		return explicitSet.removeAll(selectors);
	}

	private void addAllSiblingVariantsExcept(ItemStack excludedStack, List<ItemStack> allItems,
		Set<String> explicitSet) {
				for (ItemStack candidate : allItems) {
			if (GroupItemSelector.sameItem(candidate, excludedStack)) {
				cachedExactSelector(candidate).ifPresent(selector -> {
					if (!ItemStack.isSameItemSameComponents(candidate, excludedStack) && !isExactSelected(candidate, explicitSet)) {
						explicitSet.add(selector);
					}
				});
			}
		}
	}
}
