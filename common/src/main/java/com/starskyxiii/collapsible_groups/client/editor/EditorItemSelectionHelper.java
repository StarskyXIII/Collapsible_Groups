package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class EditorItemSelectionHelper {
	private final boolean allowExact;
	private final IdentityHashMap<ItemStack, Optional<String>> exactSelectorCache = new IdentityHashMap<>();
	private final Map<String, Optional<String>> decodedSelectorKeys = new LinkedHashMap<>();
	private Set<String> indexedSelection;
	private Map<String, Set<String>> semanticSelections = Map.of();
	private Object registryIdentity;

	EditorItemSelectionHelper() { this(true); }
	EditorItemSelectionHelper(boolean allowExact) { this.allowExact = allowExact; }

	private void ensureRegistry() {
		Object current = GroupItemSelector.registryIdentity();
		if (current != registryIdentity) {
			clearCache();
			registryIdentity = current;
		}
	}

	Optional<String> cachedExactSelector(ItemStack stack) {
		ensureRegistry();
		return exactSelectorCache.computeIfAbsent(stack, GroupItemSelector::tryExactSelector);
	}

	void clearCache() {
		exactSelectorCache.clear();
		decodedSelectorKeys.clear();
		invalidateSelections();
	}

	void invalidateSelections() { indexedSelection = null; semanticSelections = Map.of(); }

	boolean canSelectSingle(ItemStack stack) { return allowExact || !stack.hasTag(); }

	boolean canRemoveSingle(ItemStack stack, List<ItemStack> allItems, Set<String> explicitSet) {
		return allowExact || !isWholeItemSelected(stack, explicitSet) || allItems.stream().noneMatch(candidate ->
			GroupItemSelector.sameItem(candidate, stack) && !ItemStack.isSameItemSameTags(candidate, stack));
	}

	boolean isWholeItemSelected(ItemStack stack, Set<String> explicitSet) {
		return explicitSet.contains(GroupItemSelector.wholeItemSelector(stack));
	}

	boolean isExactSelected(ItemStack stack, Set<String> explicitSet) {
		return !equivalentSelectors(stack, explicitSet).isEmpty();
	}

	private String preferredSelector(ItemStack stack) {
		return !stack.hasTag() ? GroupItemSelector.wholeItemSelector(stack) : GroupItemSelector.exactSelector(stack);
	}

	boolean hasPreferredSelection(ItemStack stack, Set<String> explicitSet) {
		return isExactSelected(stack, explicitSet) || (!stack.hasTag() && isWholeItemSelected(stack, explicitSet));
	}

	void toggleSingleSelection(ItemStack stack, Set<String> explicitSet) {
		if (!canSelectSingle(stack)) return;
		Set<String> equivalents = equivalentSelectors(stack, explicitSet);
		if (!equivalents.isEmpty()) {
			explicitSet.removeAll(equivalents);
			invalidateSelections();
			return;
		}
		String selector = preferredSelector(stack);
		if (explicitSet.remove(selector)) { invalidateSelections(); return; }
		if (GroupItemSelector.isExactSelector(selector)) explicitSet.remove(GroupItemSelector.wholeItemSelector(stack));
		else removeExactSelectionsForItem(stack, explicitSet);
		explicitSet.add(selector);
		invalidateSelections();
	}

	boolean addSingleSelectionIfAbsent(ItemStack stack, Set<String> explicitSet) {
		if (!canSelectSingle(stack) || hasPreferredSelection(stack, explicitSet)) return false;
		String selector = preferredSelector(stack);
		boolean changed = GroupItemSelector.isExactSelector(selector)
			? explicitSet.remove(GroupItemSelector.wholeItemSelector(stack)) : removeExactSelectionsForItem(stack, explicitSet);
		changed |= explicitSet.add(selector);
		invalidateSelections();
		return changed;
	}

	void toggleWholeItemSelection(ItemStack stack, Set<String> explicitSet) {
		String selector = GroupItemSelector.wholeItemSelector(stack);
		if (explicitSet.remove(selector)) { invalidateSelections(); return; }
		removeExactSelectionsForItem(stack, explicitSet);
		explicitSet.add(selector);
		invalidateSelections();
	}

	void removeSingleSelection(ItemStack stack, List<ItemStack> allItems, Set<String> explicitSet) {
		if (!canRemoveSingle(stack, allItems, explicitSet)) return;
		Set<String> equivalents = equivalentSelectors(stack, explicitSet);
		if (!equivalents.isEmpty()) {
			explicitSet.removeAll(equivalents);
			invalidateSelections();
			return;
		}
		if (explicitSet.remove(GroupItemSelector.wholeItemSelector(stack)) && allowExact)
			addAllSiblingVariantsExcept(stack, allItems, explicitSet);
		invalidateSelections();
	}

	void removeAllSelectionsForItem(ItemStack stack, Set<String> explicitSet) {
		explicitSet.removeIf(selector -> GroupItemSelector.isSelectorForSameItem(selector, stack));
		invalidateSelections();
	}

	private Set<String> equivalentSelectors(ItemStack stack, Set<String> explicitSet) {
		ensureRegistry();
		if (indexedSelection != explicitSet) {
			Map<String, Set<String>> index = new LinkedHashMap<>();
			for (String selector : explicitSet) {
				if (!GroupItemSelector.isExactSelector(selector)) continue;
				decodedSelectorKeys.computeIfAbsent(selector, value -> GroupItemSelector.decodeExactSelector(value)
					.flatMap(GroupItemSelector::tryExactSelector))
					.ifPresent(key -> index.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(selector));
			}
			semanticSelections = index;
			indexedSelection = explicitSet;
		}
		return cachedExactSelector(stack).map(key -> semanticSelections.getOrDefault(key, Set.of())).orElse(Set.of());
	}

	private boolean removeExactSelectionsForItem(ItemStack stack, Set<String> explicitSet) {
		boolean changed = explicitSet.removeIf(selector -> GroupItemSelector.isExactSelector(selector)
			&& GroupItemSelector.isSelectorForSameItem(selector, stack));
		if (changed) invalidateSelections();
		return changed;
	}

	private void addAllSiblingVariantsExcept(ItemStack excludedStack, List<ItemStack> allItems, Set<String> explicitSet) {
		for (ItemStack candidate : allItems) {
			if (GroupItemSelector.sameItem(candidate, excludedStack) && !ItemStack.isSameItemSameTags(candidate, excludedStack))
				cachedExactSelector(candidate).ifPresent(explicitSet::add);
		}
	}
}
