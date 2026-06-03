package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.core.GroupItemSelector;
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

	void toggleSingleSelection(ItemStack stack) {
		String exactSelector = GroupItemSelector.exactSelector(stack);
		if (explicitSet.remove(exactSelector)) {
			onContentsDraftChanged.run();
			return;
		}
		explicitSet.remove(GroupItemSelector.wholeItemSelector(stack));
		explicitSet.add(exactSelector);
		onContentsDraftChanged.run();
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

	private void removeExactSelectionsForItem(ItemStack stack) {
		Set<String> selectors = explicitSet.stream()
			.filter(GroupItemSelector::isExactSelector)
			.filter(selector -> GroupItemSelector.isSelectorForSameItem(selector, stack))
			.collect(Collectors.toSet());
		explicitSet.removeAll(selectors);
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
