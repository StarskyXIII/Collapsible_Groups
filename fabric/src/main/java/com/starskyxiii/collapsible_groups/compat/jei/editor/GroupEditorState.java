package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
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
	String editId;
	String editName;
	boolean editEnabled;

	final GroupFilterEditorDraft draft;
	final List<String> editTags;
	final Set<String> explicitSet;
	final List<String> editFluidIds;
	final List<String> editFluidTags;
	final EditorItemSelectionHelper itemSelection;

	private final EditorStateCore core;

	GroupEditorState(GroupDefinition existing) {
		this.draft = GroupFilterEditorDraft.empty();
		this.core = new EditorStateCore(existing, this::refreshContentsDraftFromRules);

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
	}

	Optional<String> cachedExactSelector(ItemStack stack) {
		return itemSelection.cachedExactSelector(stack);
	}

	Optional<GroupFilter> buildCurrentFilter() {
		return core.buildCurrentFilter();
	}

	GroupDefinition buildPreviewDefinition() {
		return core.buildPreviewDefinition(editId, editName, editEnabled);
	}

	boolean canUseIndexedItemPreview() {
		return core.canUseIndexedItemPreview();
	}

	boolean canEditContents() {
		return core.canEditContents();
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
		return core.trySave(editId, editName, editEnabled);
	}

	boolean canSave() {
		return core.canSave(editName);
	}

	List<Component> saveBlockedTooltip() {
		return core.saveBlockedTooltip(editName);
	}

	@Override
	public String filterSummary() {
		return core.filterSummary();
	}

	String previewOwnershipNote() {
		return core.previewOwnershipNote();
	}

	@Override
	public List<GroupFilterRuleDraft.FlatNode> flattenedRuleNodes() {
		return core.flattenedRuleNodes();
	}

	@Override
	public GroupFilterRuleDraft.Node selectedRuleNode() {
		return core.selectedRuleNode();
	}

	@Override
	public void selectRuleNode(GroupFilterRuleDraft.Node node) {
		core.selectRuleNode(node);
	}

	@Override
	public void ensureRuleSelection() {
		core.ensureRuleSelection();
	}

	@Override
	public boolean canInsertRuleRelative() {
		return core.canInsertRuleRelative();
	}

	@Override
	public boolean canWrapSelectedRule(GroupFilterRuleDraft.NodeKind kind) {
		return core.canWrapSelectedRule(kind);
	}

	@Override
	public boolean canDeleteSelectedRule() {
		return core.canDeleteSelectedRule();
	}

	@Override
	public GroupFilterRuleDraft.Node insertRuleRelative(GroupFilterRuleDraft.NodeKind kind) {
		return core.insertRuleRelative(kind);
	}

	@Override
	public GroupFilterRuleDraft.Node wrapSelectedRule(GroupFilterRuleDraft.NodeKind kind) {
		return core.wrapSelectedRule(kind);
	}

	@Override
	public void deleteSelectedRule() {
		core.deleteSelectedRule();
	}

	@Override
	public void markRulesChanged() {
		core.markRulesChanged();
	}

	@Override
	public List<Component> currentValidationErrors() {
		return core.currentValidationErrors();
	}

	private void syncRulesFromContentsDraft() {
		core.syncRulesFromContentsDraft(draft);
	}

	private void refreshContentsDraftFromRules() {
		clearContentsDraft();
		Optional<GroupFilter> filter = buildCurrentFilter();
		if (filter.isEmpty()) {
			core.setContentsQuickEditAvailable(!core.hasRulesRoot());
			return;
		}

		GroupFilterEditorDraft.DecodeResult decoded = GroupFilterEditorDraft.decode(filter.get());
		core.setContentsQuickEditAvailable(decoded.structurallyEditable() && !containsGenericDraftNodes(decoded.draft()));
		if (core.canEditContents()) {
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
