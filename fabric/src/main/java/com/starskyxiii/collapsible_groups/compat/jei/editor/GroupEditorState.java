package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.core.GroupFilterClauseFormatter;
import com.starskyxiii.collapsible_groups.core.GroupFilterSummaryFormatter;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupItemSelector;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds all mutable edit state for {@link GroupEditorScreen} and exposes the
 * business logic for item selection, filter draft building, and saving.
 * Item-only variant (no fluid/generic support on Fabric).
 */
final class GroupEditorState {
	private static final GroupFilter EMPTY_PREVIEW_FILTER = Filters.itemTag("minecraft:__cg_preview_empty__");

	String editId;
	String editName;
	boolean editEnabled;

	final GroupFilterEditorDraft draft;
	final List<String> editTags;
	final Set<String> explicitSet;

	private final IdentityHashMap<ItemStack, Optional<String>> exactSelectorCache = new IdentityHashMap<>();
	private final GroupDefinition existingDefinition;
	private final boolean structurallyEditable;
	private final Set<GroupFilterEditorDraft.UnsupportedEditorNodeKind> unsupportedNodeKinds;

	GroupEditorState(GroupDefinition existing) {
		this.existingDefinition = existing;
		GroupFilterEditorDraft.DecodeResult decoded = existing != null
			? GroupFilterEditorDraft.decode(existing.filter())
			: new GroupFilterEditorDraft.DecodeResult(GroupFilterEditorDraft.empty(), true, Set.of());
		this.structurallyEditable = existing == null || decoded.structurallyEditable();
		this.unsupportedNodeKinds = decoded.unsupportedNodeKinds();
		this.draft = structurallyEditable ? decoded.draft() : GroupFilterEditorDraft.empty();

		if (existing != null) {
			this.editId = existing.id();
			this.editName = existing.displayName().fallback();
			this.editEnabled = existing.enabled();
		} else {
			this.editId = null;
			this.editName = "";
			this.editEnabled = true;
		}

		this.editTags = draft.itemTags();
		this.explicitSet = draft.explicitItemSelectors();
	}

	Optional<String> cachedExactSelector(ItemStack stack) {
		return exactSelectorCache.computeIfAbsent(stack, GroupItemSelector::tryExactSelector);
	}

	Optional<GroupFilter> buildCurrentFilter() {
		return draft.toFilter();
	}

	GroupDefinition buildPreviewDefinition() {
		if (!structurallyEditable && existingDefinition != null) {
			return existingDefinition;
		}
		GroupFilter filter = buildCurrentFilter().orElse(EMPTY_PREVIEW_FILTER);
		return new GroupDefinition(
			editId != null ? editId : "__preview__",
			editName,
			editEnabled,
			filter
		);
	}

	boolean isStructurallyEditable() {
		return structurallyEditable;
	}

	boolean isWholeItemSelected(ItemStack stack) {
		return explicitSet.contains(GroupItemSelector.wholeItemSelector(stack));
	}

	boolean isExactSelected(ItemStack stack) {
		return cachedExactSelector(stack).map(explicitSet::contains).orElse(false);
	}

	void toggleSingleSelection(ItemStack stack) {
		String exactSelector = GroupItemSelector.exactSelector(stack);
		if (explicitSet.remove(exactSelector)) return;
		explicitSet.remove(GroupItemSelector.wholeItemSelector(stack));
		explicitSet.add(exactSelector);
	}

	void toggleWholeItemSelection(ItemStack stack) {
		String wholeItemSelector = GroupItemSelector.wholeItemSelector(stack);
		if (explicitSet.remove(wholeItemSelector)) return;
		removeExactSelectionsForItem(stack);
		explicitSet.add(wholeItemSelector);
	}

	void removeSingleSelection(ItemStack stack, List<ItemStack> allItems) {
		String exactSelector = GroupItemSelector.exactSelector(stack);
		if (explicitSet.remove(exactSelector)) return;
		String wholeItemSelector = GroupItemSelector.wholeItemSelector(stack);
		if (explicitSet.remove(wholeItemSelector)) {
			addAllSiblingVariantsExcept(stack, allItems);
		}
	}

	void removeAllSelectionsForItem(ItemStack stack) {
		Set<String> selectors = explicitSet.stream()
			.filter(selector -> GroupItemSelector.isSelectorForSameItem(selector, stack))
			.collect(Collectors.toSet());
		explicitSet.removeAll(selectors);
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

	void syncEditItems() {
		// The draft now owns the explicit selector set directly.
	}

	Optional<GroupDefinition> trySave() {
		if (!canSave()) return Optional.empty();
		Optional<GroupFilter> filter = buildCurrentFilter();
		String id = (editId != null && !editId.isEmpty()) ? editId : GroupRegistry.generateUniqueId(editName);
		try {
			GroupDefinition saved = new GroupDefinition(id, editName, editEnabled, filter.get());
			GroupRegistry.saveQuietly(saved);
			return Optional.of(saved);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	boolean isSaveBlockedByUnsupportedFilter() {
		return !structurallyEditable;
	}

	String filterEditStatusLabel() {
		return Component.translatable(structurallyEditable
			? ModTranslationKeys.EDITOR_FILTER_EDITABLE
			: ModTranslationKeys.EDITOR_FILTER_READONLY).getString();
	}

	String filterSummary() {
		GroupFilter filter = summaryFilter();
		if (filter == null) return Component.translatable(ModTranslationKeys.EDITOR_RULES_NO_FILTER).getString();
		return GroupFilterSummaryFormatter.format(filter);
	}

	String unsupportedReasonSummary() {
		if (unsupportedNodeKinds.isEmpty()) {
			return Component.translatable(structurallyEditable
				? ModTranslationKeys.EDITOR_UNSUPPORTED_EDITABLE
				: ModTranslationKeys.EDITOR_UNSUPPORTED_UNAVAILABLE).getString();
		}
		return unsupportedNodeKinds.stream()
			.map(k -> Component.translatable(k.reasonKey()).getString())
			.collect(Collectors.joining(", "));
	}

	String unsupportedNodeKindsLabel() {
		if (unsupportedNodeKinds.isEmpty()) {
			return Component.translatable(ModTranslationKeys.EDITOR_UNSUPPORTED_NONE).getString();
		}
		return unsupportedNodeKinds.stream()
			.map(k -> Component.translatable(k.labelKey()).getString())
			.collect(Collectors.joining(", "));
	}

	boolean canSave() {
		return !(editName == null || editName.isBlank())
			&& structurallyEditable
			&& buildCurrentFilter().isPresent();
	}

	List<Component> saveBlockedTooltip() {
		if (editName == null || editName.isBlank()) {
			return List.of(
				Component.translatable(ModTranslationKeys.EDITOR_SAVE_ERROR),
				Component.translatable(ModTranslationKeys.EDITOR_SAVE_BLOCKED_NO_NAME)
			);
		}
		if (!structurallyEditable) {
			return List.of(
				Component.translatable(ModTranslationKeys.EDITOR_SAVE_ERROR),
				Component.translatable(ModTranslationKeys.EDITOR_SAVE_BLOCKED_READONLY, unsupportedReasonSummary())
			);
		}
		if (buildCurrentFilter().isEmpty()) {
			return List.of(
				Component.translatable(ModTranslationKeys.EDITOR_SAVE_ERROR),
				Component.translatable(ModTranslationKeys.EDITOR_SAVE_BLOCKED_NO_FILTER)
			);
		}
		return List.of();
	}

	String previewOwnershipNote() {
		return Component.translatable(ModTranslationKeys.EDITOR_PREVIEW_NOTE).getString();
	}

	GroupFilter rulesDisplayFilter() {
		return summaryFilter();
	}

	boolean shouldShowRuleClauses() {
		return GroupFilterClauseFormatter.shouldDisplay(summaryFilter());
	}

	private GroupFilter summaryFilter() {
		if (!structurallyEditable && existingDefinition != null) {
			return existingDefinition.filter();
		}
		return buildCurrentFilter().orElse(null);
	}
}
