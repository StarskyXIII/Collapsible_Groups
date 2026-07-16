package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class EditorItemSelectionHelper {
	private final Set<String> explicitSet;
	private final Runnable onContentsDraftChanged;
	private final IdentityHashMap<ItemStack, Optional<String>> exactSelectorCache = new IdentityHashMap<>();

	EditorItemSelectionHelper(Set<String> explicitSet, Runnable onContentsDraftChanged) {
		this.explicitSet = explicitSet;
		this.onContentsDraftChanged = onContentsDraftChanged;
	}

	Optional<String> cachedExactSelector(ItemStack stack) {
		return exactSelectorCache.computeIfAbsent(stack, GroupItemSelector::tryExactSelector);
	}

	boolean isWholeItemSelected(ItemStack stack) {
		return explicitSet.contains(GroupItemSelector.wholeItemSelector(stack));
	}

	boolean isExactSelected(ItemStack stack) {
		return cachedExactSelector(stack).map(explicitSet::contains).orElse(false);
	}

	/**
	 * the selector stored for a plain single-click. Component-less items store the cheap,
	 * broad whole-item id (so common vanilla items never bloat into exact-stack rules); items that
	 * carry a component patch keep the exact-stack selector. Plain-clicking therefore toggles the
	 * whole-item selection on/off for component-less items (a second click removes, it no longer
	 * switches to an exact variant), while items with components keep their prior exact behavior.
	 */
	private String preferredSelector(ItemStack stack) {
		return stack.getComponentsPatch().isEmpty()
			? GroupItemSelector.wholeItemSelector(stack)
			: GroupItemSelector.exactSelector(stack);
	}

	void toggleSingleSelection(ItemStack stack) {
		String preferredSelector = preferredSelector(stack);
		if (explicitSet.remove(preferredSelector)) {
			onContentsDraftChanged.run();
			return;
		}
		if (GroupItemSelector.isExactSelector(preferredSelector)) {
			explicitSet.remove(GroupItemSelector.wholeItemSelector(stack));
		} else {
			removeExactSelectionsForItem(stack);
		}
		explicitSet.add(preferredSelector);
		onContentsDraftChanged.run();
	}

	boolean addSingleSelectionIfAbsent(ItemStack stack) {
		String preferredSelector = preferredSelector(stack);
		if (explicitSet.contains(preferredSelector)) {
			return false;
		}
		boolean changed;
		if (GroupItemSelector.isExactSelector(preferredSelector)) {
			changed = explicitSet.remove(GroupItemSelector.wholeItemSelector(stack));
		} else {
			changed = removeExactSelectionsForItem(stack);
		}
		changed |= explicitSet.add(preferredSelector);
		if (changed) {
			onContentsDraftChanged.run();
		}
		return changed;
	}

	void toggleWholeItemSelection(ItemStack stack) {
		String wholeItemSelector = GroupItemSelector.wholeItemSelector(stack);
		if (explicitSet.remove(wholeItemSelector)) {
			onContentsDraftChanged.run();
			return;
		}
		removeExactSelectionsForItem(stack);
		explicitSet.add(wholeItemSelector);
		onContentsDraftChanged.run();
	}

	void removeSingleSelection(ItemStack stack, List<ItemStack> allItems) {
		String exactSelector = GroupItemSelector.exactSelector(stack);
		if (explicitSet.remove(exactSelector)) {
			onContentsDraftChanged.run();
			return;
		}
		String wholeItemSelector = GroupItemSelector.wholeItemSelector(stack);
		if (explicitSet.remove(wholeItemSelector)) {
			addAllSiblingVariantsExcept(stack, allItems);
			onContentsDraftChanged.run();
		}
	}

	void removeAllSelectionsForItem(ItemStack stack) {
		Set<String> selectors = explicitSet.stream()
			.filter(selector -> GroupItemSelector.isSelectorForSameItem(selector, stack))
			.collect(Collectors.toSet());
		explicitSet.removeAll(selectors);
		onContentsDraftChanged.run();
	}

	private boolean removeExactSelectionsForItem(ItemStack stack) {
		Set<String> selectors = explicitSet.stream()
			.filter(GroupItemSelector::isExactSelector)
			.filter(selector -> GroupItemSelector.isSelectorForSameItem(selector, stack))
			.collect(Collectors.toSet());
		return explicitSet.removeAll(selectors);
	}

	private void addAllSiblingVariantsExcept(ItemStack excludedStack, List<ItemStack> allItems) {
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
