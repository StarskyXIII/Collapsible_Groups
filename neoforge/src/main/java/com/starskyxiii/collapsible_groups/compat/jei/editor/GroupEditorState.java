package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Holds all mutable edit state for {@link GroupEditorScreen}.
 *
 * <p>The editor now tracks two parallel representations:
 * <ul>
 * <li>{@link #draft}: legacy flat selectors used by the contents tab and the
 *     indexed preview fast-path.</li>
 * <li>{@link EditorStateCore}: full filter tree used by the rules tab and persistence.</li>
 * </ul>
 */
final class GroupEditorState implements EditorRulesState {
	// --- Identity ---
	String editId;
	String editName;
	boolean editEnabled;

	// --- Contents-tab quick-edit draft ---
	final GroupFilterEditorDraft draft;
	final List<String> editTags;
	final Set<String> explicitSet;
	final List<String> editFluidIds;
	final List<String> editFluidTags;
	final List<GroupFilterEditorDraft.GenericValue> editGenericIds;
	final List<GroupFilterEditorDraft.GenericValue> editGenericTags;
	final EditorItemSelectionHelper itemSelection;
	final EditorGenericSelectionHelper genericSelection;

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
		this.editGenericIds = draft.genericIds();
		this.editGenericTags = draft.genericTags();
		this.itemSelection = new EditorItemSelectionHelper(explicitSet, this::syncRulesFromContentsDraft);
		this.genericSelection = new EditorGenericSelectionHelper(editGenericIds, editGenericTags,
			this::syncRulesFromContentsDraft);

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
		// No-op: the contents-tab collections are live. Kept for existing call sites.
	}

	boolean isFluidSelected(FluidStack fluid) {
		String id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
		return editFluidIds.contains(id);
	}

	void toggleFluidSelection(FluidStack fluid) {
		String id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
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

	void removeFluidSelection(FluidStack fluid) {
		String id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
		if (editFluidIds.remove(id)) {
			syncRulesFromContentsDraft();
		}
	}

	boolean isGenericSelected(GenericIngredientView entry) {
		return genericSelection.isSelected(entry);
	}

	boolean isGenericTagMatched(GenericIngredientView entry) {
		return genericSelection.isTagMatched(entry);
	}

	void toggleGenericSelection(GenericIngredientView entry) {
		genericSelection.toggleSelection(entry);
	}

	void addGenericId(String typeId, String id) {
		genericSelection.addId(typeId, id);
	}

	void removeGenericSelection(GenericIngredientView entry) {
		genericSelection.removeSelection(entry);
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

	String pendingIdLabel() {
		return core.pendingIdLabel(editId, editName);
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

	String contentsEditStatusLabel() {
		return core.contentsEditStatusLabel();
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
		core.setContentsQuickEditAvailable(decoded.structurallyEditable());
		if (core.canEditContents()) {
			copyContentsDraft(decoded.draft());
		}
	}

	private void clearContentsDraft() {
		explicitSet.clear();
		editTags.clear();
		editFluidIds.clear();
		editFluidTags.clear();
		editGenericIds.clear();
		editGenericTags.clear();
	}

	private void copyContentsDraft(GroupFilterEditorDraft source) {
		explicitSet.addAll(source.explicitItemSelectors());
		editTags.addAll(source.itemTags());
		editFluidIds.addAll(source.fluidIds());
		editFluidTags.addAll(source.fluidTags());
		editGenericIds.addAll(source.genericIds());
		editGenericTags.addAll(source.genericTags());
	}

}
