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

	Optional<String> cachedExactSelector(ItemStack stack) {
		return exactSelectorCache.computeIfAbsent(stack, GroupItemSelector::tryExactSelector);
	}

	void clearCache() { exactSelectorCache.clear(); }

	boolean isWholeItemSelected(ItemStack stack, Set<String> explicitSet) {
		return explicitSet.contains(GroupItemSelector.wholeItemSelector(stack));
	}

	boolean isExactSelected(ItemStack stack, Set<String> explicitSet) {
		return cachedExactSelector(stack).map(explicitSet::contains).orElse(false);
	}

	/**
	 * the selector stored for a plain single-click. Component-less items store the cheap,
	 * broad whole-item id (so common vanilla items never bloat into exact-stack rules); items that
	 * carry a component patch keep the exact-stack selector. Plain-clicking therefore toggles the
	 * whole-item selection on/off for component-less items, while items with components use exact
	 * selectors.
	 */
	private String preferredSelector(ItemStack stack) {
		return !stack.hasTag()
			? GroupItemSelector.wholeItemSelector(stack)
			: GroupItemSelector.exactSelector(stack);
	}

	boolean hasPreferredSelection(ItemStack stack, Set<String> explicitSet) {
		return explicitSet.contains(preferredSelector(stack));
	}

	void toggleSingleSelection(ItemStack stack, Set<String> explicitSet) {
		String preferredSelector = preferredSelector(stack);
		if (explicitSet.remove(preferredSelector)) {
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
		String preferredSelector = preferredSelector(stack);
		if (explicitSet.contains(preferredSelector)) {
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
		String wholeItemSelector = GroupItemSelector.wholeItemSelector(stack);
		if (explicitSet.remove(wholeItemSelector)) {
			return;
		}
		removeExactSelectionsForItem(stack, explicitSet);
		explicitSet.add(wholeItemSelector);
	}

	void removeSingleSelection(ItemStack stack, List<ItemStack> allItems, Set<String> explicitSet) {
		String exactSelector = GroupItemSelector.exactSelector(stack);
		if (explicitSet.remove(exactSelector)) {
			return;
		}
		String wholeItemSelector = GroupItemSelector.wholeItemSelector(stack);
		if (explicitSet.remove(wholeItemSelector)) {
			addAllSiblingVariantsExcept(stack, allItems, explicitSet);
		}
	}

	void removeAllSelectionsForItem(ItemStack stack, Set<String> explicitSet) {
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
		String excludedSelector = GroupItemSelector.exactSelector(excludedStack);
		for (ItemStack candidate : allItems) {
			if (GroupItemSelector.sameItem(candidate, excludedStack)) {
				cachedExactSelector(candidate).ifPresent(selector -> {
					if (!selector.equals(excludedSelector)) {
						explicitSet.add(selector);
					}
				});
			}
		}
	}
}
