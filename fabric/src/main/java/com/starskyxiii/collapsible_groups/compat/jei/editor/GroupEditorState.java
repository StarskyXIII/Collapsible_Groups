package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.core.GroupFilterSummaryFormatter;
import com.starskyxiii.collapsible_groups.core.GroupFilterValidator;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Holds all mutable edit state for {@link GroupEditorScreen}.
 *
 * <p>The Fabric editor supports item and fluid contents editing, and mirrors the richer rules workflow:
 * a flat contents draft powers quick item editing while a rule-tree draft powers the
 * Rules tab and persistence. Groups containing generic/custom draft nodes keep contents
 * quick-edit readonly until the Fabric editor gains those source tabs.
 */
final class GroupEditorState implements EditorRulesState {
	private static final GroupFilter EMPTY_PREVIEW_FILTER = Filters.itemTag("minecraft:__cg_preview_empty__");

	String editId;
	String editName;
	boolean editEnabled;

	final GroupFilterEditorDraft draft;
	final List<String> editTags;
	final Set<String> explicitSet;
	final List<String> editFluidIds;
	final List<String> editFluidTags;
	final EditorItemSelectionHelper itemSelection;

	final GroupFilterRuleDraft ruleDraft;
	private GroupFilterRuleDraft.Node selectedRuleNode;
	private boolean contentsQuickEditAvailable;

	private final GroupDefinition existingDefinition;
	private GroupFilter lastValidPreviewFilter = EMPTY_PREVIEW_FILTER;

	GroupEditorState(GroupDefinition existing) {
		this.existingDefinition = existing;
		this.draft = GroupFilterEditorDraft.empty();
		this.ruleDraft = existing != null ? GroupFilterRuleDraft.decode(existing.filter()) : GroupFilterRuleDraft.empty();
		this.selectedRuleNode = ruleDraft.root();

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
		this.editFluidIds = draft.fluidIds();
		this.editFluidTags = draft.fluidTags();
		this.itemSelection = new EditorItemSelectionHelper(explicitSet, this::syncRulesFromContentsDraft);

		refreshContentsDraftFromRules();
		buildCurrentFilter()
			.filter(filter -> GroupFilterValidator.validate(filter).isEmpty())
			.ifPresent(filter -> lastValidPreviewFilter = filter);
	}

	Optional<String> cachedExactSelector(ItemStack stack) {
		return itemSelection.cachedExactSelector(stack);
	}

	Optional<GroupFilter> buildCurrentFilter() {
		return ruleDraft.toFilter();
	}

	GroupDefinition buildPreviewDefinition() {
		Optional<GroupFilter> currentFilter = buildCurrentFilter();
		GroupFilter previewFilter;
		if (currentFilter.isEmpty()) {
			previewFilter = EMPTY_PREVIEW_FILTER;
		} else {
			previewFilter = currentFilter
				.filter(filter -> GroupFilterValidator.validate(filter).isEmpty())
				.map(filter -> {
					lastValidPreviewFilter = filter;
					return filter;
				})
				.orElse(lastValidPreviewFilter);
		}
		return GroupEditorDefinitionFactory.create(
			editId != null ? editId : "__preview__",
			editName,
			editEnabled,
			previewFilter,
			existingDefinition
		);
	}

	boolean canUseIndexedItemPreview() {
		return contentsQuickEditAvailable;
	}

	boolean canEditContents() {
		return contentsQuickEditAvailable;
	}

	boolean isWholeItemSelected(ItemStack stack) {
		return itemSelection.isWholeItemSelected(stack);
	}

	boolean isExactSelected(ItemStack stack) {
		return itemSelection.isExactSelected(stack);
	}

	void toggleSingleSelection(ItemStack stack) {
		itemSelection.toggleSingleSelection(stack);
	}

	void toggleWholeItemSelection(ItemStack stack) {
		itemSelection.toggleWholeItemSelection(stack);
	}

	void removeSingleSelection(ItemStack stack, List<ItemStack> allItems) {
		itemSelection.removeSingleSelection(stack, allItems);
	}

	void removeAllSelectionsForItem(ItemStack stack) {
		itemSelection.removeAllSelectionsForItem(stack);
	}

	void syncEditItems() {
		// No-op: the contents collections are live views backed by the draft.
	}

	boolean isFluidSelected(IJeiFluidIngredient fluid) {
		return editFluidIds.contains(fluidId(fluid));
	}

	void toggleFluidSelection(IJeiFluidIngredient fluid) {
		String id = fluidId(fluid);
		if (!editFluidIds.remove(id)) {
			editFluidIds.add(id);
		}
		syncRulesFromContentsDraft();
	}

	void addFluidId(String id) {
		if (!editFluidIds.contains(id)) {
			editFluidIds.add(id);
			syncRulesFromContentsDraft();
		}
	}

	void removeFluidSelection(IJeiFluidIngredient fluid) {
		if (editFluidIds.remove(fluidId(fluid))) {
			syncRulesFromContentsDraft();
		}
	}

	Optional<GroupDefinition> trySave() {
		if (!canSave()) return Optional.empty();
		Optional<GroupFilter> filter = buildCurrentFilter();
		String id = (editId != null && !editId.isEmpty()) ? editId : GroupRegistry.generateUniqueId(editName);
		try {
			GroupDefinition saved = GroupEditorDefinitionFactory.create(id, editName, editEnabled, filter.get(), existingDefinition);
			GroupRegistry.saveQuietly(saved);
			return Optional.of(saved);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	boolean canSave() {
		return !(editName == null || editName.isBlank())
			&& buildCurrentFilter().isPresent()
			&& currentValidationErrors().isEmpty();
	}

	List<Component> saveBlockedTooltip() {
		if (editName == null || editName.isBlank()) {
			return List.of(
				Component.translatable(ModTranslationKeys.EDITOR_SAVE_ERROR),
				Component.translatable(ModTranslationKeys.EDITOR_SAVE_BLOCKED_NO_NAME)
			);
		}
		if (buildCurrentFilter().isEmpty()) {
			return List.of(
				Component.translatable(ModTranslationKeys.EDITOR_SAVE_ERROR),
				Component.translatable(ModTranslationKeys.EDITOR_SAVE_BLOCKED_NO_FILTER)
			);
		}
		List<Component> errors = currentValidationErrors();
		if (!errors.isEmpty()) {
			return List.of(
				Component.translatable(ModTranslationKeys.EDITOR_SAVE_ERROR),
				errors.getFirst()
			);
		}
		return List.of();
	}

	@Override
	public String filterSummary() {
		GroupFilter filter = buildCurrentFilter().orElse(null);
		if (filter == null) return Component.translatable(ModTranslationKeys.EDITOR_RULES_NO_FILTER).getString();
		return GroupFilterSummaryFormatter.format(filter);
	}

	String previewOwnershipNote() {
		return Component.translatable(ModTranslationKeys.EDITOR_PREVIEW_NOTE).getString();
	}

	@Override
	public List<GroupFilterRuleDraft.FlatNode> flattenedRuleNodes() {
		return ruleDraft.flatten();
	}

	@Override
	public GroupFilterRuleDraft.Node selectedRuleNode() {
		return selectedRuleNode;
	}

	@Override
	public void selectRuleNode(GroupFilterRuleDraft.Node node) {
		selectedRuleNode = node;
	}

	@Override
	public void ensureRuleSelection() {
		if (selectedRuleNode == null) {
			selectedRuleNode = ruleDraft.root();
		}
	}

	@Override
	public boolean canInsertRuleRelative() {
		return ruleDraft.canInsertRelativeTo(selectedRuleNode);
	}

	@Override
	public boolean canWrapSelectedRule(GroupFilterRuleDraft.NodeKind kind) {
		return ruleDraft.canWrap(selectedRuleNode, kind);
	}

	@Override
	public boolean canDeleteSelectedRule() {
		return selectedRuleNode != null;
	}

	@Override
	public GroupFilterRuleDraft.Node insertRuleRelative(GroupFilterRuleDraft.NodeKind kind) {
		GroupFilterRuleDraft.Node node = ruleDraft.insertRelativeTo(selectedRuleNode, kind);
		if (node != null) {
			selectedRuleNode = node;
			refreshContentsDraftFromRules();
		}
		return node;
	}

	@Override
	public GroupFilterRuleDraft.Node wrapSelectedRule(GroupFilterRuleDraft.NodeKind kind) {
		if (selectedRuleNode == null) {
			return null;
		}
		GroupFilterRuleDraft.Node node = ruleDraft.wrap(selectedRuleNode, kind);
		if (node != null) {
			selectedRuleNode = node;
			refreshContentsDraftFromRules();
		}
		return node;
	}

	@Override
	public void deleteSelectedRule() {
		if (selectedRuleNode == null) {
			return;
		}
		selectedRuleNode = ruleDraft.delete(selectedRuleNode);
		if (selectedRuleNode == null) {
			selectedRuleNode = ruleDraft.root();
		}
		refreshContentsDraftFromRules();
	}

	@Override
	public void markRulesChanged() {
		refreshContentsDraftFromRules();
	}

	@Override
	public List<Component> currentValidationErrors() {
		return buildCurrentFilter()
			.map(GroupFilterValidator::validateComponents)
			.orElse(List.of());
	}

	private void syncRulesFromContentsDraft() {
		if (!contentsQuickEditAvailable) {
			return;
		}
		GroupFilterRuleDraft replacement = draft.toFilter()
			.map(GroupFilterRuleDraft::decode)
			.orElseGet(GroupFilterRuleDraft::empty);
		ruleDraft.replaceWith(replacement);
		selectedRuleNode = ruleDraft.root();
	}

	private void refreshContentsDraftFromRules() {
		clearContentsDraft();
		Optional<GroupFilter> filter = buildCurrentFilter();
		if (filter.isEmpty()) {
			contentsQuickEditAvailable = !ruleDraft.hasRoot();
			return;
		}

		GroupFilterEditorDraft.DecodeResult decoded = GroupFilterEditorDraft.decode(filter.get());
		contentsQuickEditAvailable = decoded.structurallyEditable() && !containsGenericDraftNodes(decoded.draft());
		if (contentsQuickEditAvailable) {
			copyContentsDraft(decoded.draft());
		}
	}

	private void clearContentsDraft() {
		explicitSet.clear();
		editTags.clear();
		editFluidIds.clear();
		editFluidTags.clear();
	}

	private void copyContentsDraft(GroupFilterEditorDraft source) {
		explicitSet.addAll(source.explicitItemSelectors());
		editTags.addAll(source.itemTags());
		editFluidIds.addAll(source.fluidIds());
		editFluidTags.addAll(source.fluidTags());
	}

	private static boolean containsGenericDraftNodes(GroupFilterEditorDraft draft) {
		return !draft.genericIds().isEmpty()
			|| !draft.genericTags().isEmpty();
	}

	private static String fluidId(IJeiFluidIngredient fluid) {
		return BuiltInRegistries.FLUID.getKey(fluid.getFluidVariant().getFluid()).toString();
	}
}
