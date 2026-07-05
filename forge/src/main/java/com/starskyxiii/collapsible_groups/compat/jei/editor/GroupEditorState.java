package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.RuleTagResolution;
import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Holds all mutable edit state for {@link GroupEditorScreen}.
 *
 * <p>The Forge editor supports item, fluid, and generic/custom quick-editing
 * through the shared contents draft while preserving the richer Rules workflow.
 */
final class GroupEditorState implements EditorRulesState {
	String editId;
	String editName;
	boolean editEnabled;
	private boolean nameTouched;

	final GroupFilterEditorDraft draft;
	final List<String> editTags;
	private final Set<String> explicitSet;
	final List<String> editFluidIds;
	final List<String> editFluidTags;
	final List<GroupFilterEditorDraft.GenericValue> editGenericIds;
	final List<GroupFilterEditorDraft.GenericValue> editGenericTags;
	final EditorItemSelectionHelper itemSelection;
	final EditorFluidSelectionHelper fluidSelection;
	final EditorGenericSelectionHelper genericSelection;

	private final EditorStateCore core;

	GroupEditorState(GroupDefinition existing) {
		this(existing, false);
	}

	GroupEditorState(GroupDefinition existing, boolean saveAsNew) {
		this(existing, saveAsNew, null);
	}

	GroupEditorState(GroupDefinition existing, boolean saveAsNew, @Nullable String sourceGroupId) {
		this.draft = GroupFilterEditorDraft.empty();
		this.core = new EditorStateCore(existing, saveAsNew, sourceGroupId, this::refreshContentsDraftFromRules);

		if (existing != null) {
			this.editId = existing.id();
			this.editName = GroupEditorNameHelper.initialEditName(existing);
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
		this.fluidSelection = new EditorFluidSelectionHelper(editFluidIds, this::syncRulesFromContentsDraft);
		this.genericSelection = new EditorGenericSelectionHelper(editGenericIds, editGenericTags,
			this::syncRulesFromContentsDraft);

		refreshContentsDraftFromRules();
	}

	Optional<String> cachedExactSelector(ItemStack stack) {
		return itemSelection.cachedExactSelector(stack);
	}

	void setEditName(String editName) {
		if (!Objects.equals(this.editName, editName)) {
			this.editName = editName;
			this.nameTouched = true;
		}
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

	boolean addSingleSelectionIfAbsent(ItemStack stack) {
		return itemSelection.addSingleSelectionIfAbsent(stack);
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

	boolean isFluidSelected(Object fluid) {
		return fluidSelection.isSelected(fluid);
	}

	void toggleFluidSelection(Object fluid) {
		fluidSelection.toggleSelection(fluid);
	}

	void addFluidId(String id) {
		fluidSelection.addId(id);
	}

	void removeFluidSelection(Object fluid) {
		fluidSelection.removeSelection(fluid);
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
		return core.trySave(editId, editName, editEnabled, nameTouched);
	}

	boolean canSave() {
		return core.canSave(editName);
	}

	boolean isCopyDraft() {
		return core.saveAsNew();
	}

	@Nullable
	String sourceGroupId() {
		return core.sourceGroupId();
	}

	List<Component> saveBlockedTooltip() {
		return core.saveBlockedTooltip(editName);
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
	public GroupFilterRuleDraft.Node insertRuleRelativePending(GroupFilterRuleDraft.NodeKind kind) {
		return core.insertRuleRelativePending(kind);
	}

	@Override
	public boolean hasPendingRuleNode() {
		return core.hasPendingRuleNode();
	}

	@Override
	public void commitPendingRuleNode() {
		core.commitPendingRuleNode();
	}

	@Override
	public void cancelPendingRuleNode() {
		core.cancelPendingRuleNode();
	}

	@Override
	public int unresolvedRuleCount() {
		return core.unresolvedRuleCount(RuleTagResolution.RegistryLookup.INSTANCE);
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
