package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeServices;

import com.starskyxiii.collapsible_groups.group.GroupTheme;

import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.client.editor.model.RuleTagResolution;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Holds all mutable edit state for {@link GroupEditorScreen}.
 */
final class GroupEditorState implements EditorRulesState, EditorSettingsState {
	String editId;
	String editName;
	boolean editEnabled;
	AppearanceDraft appearanceDraft;
	int editPriority;
	private boolean nameTouched;

	private GroupFilterEditorDraft contentsProjection;
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
		this.core = new EditorStateCore(existing, saveAsNew, sourceGroupId, this::refreshContentsProjection);

		if (existing != null) {
			this.editId = existing.id();
			this.editName = GroupEditorNameHelper.initialEditName(existing);
			this.editEnabled = existing.enabled();
			this.appearanceDraft = AppearanceDraft.from(existing);
			this.editPriority = existing.priority();
		} else {
			this.editId = null;
			this.editName = "";
			this.editEnabled = true;
			this.appearanceDraft = AppearanceDraft.fromIconIds(List.of(), com.starskyxiii.collapsible_groups.group.GroupTheme.EMPTY);
			this.editPriority = 0;
		}

		this.itemSelection = new EditorItemSelectionHelper();
		this.fluidSelection = new EditorFluidSelectionHelper();
		this.genericSelection = new EditorGenericSelectionHelper();

		refreshContentsProjection();
	}

	Optional<String> cachedExactSelector(ItemStack stack) {
		return itemSelection.cachedExactSelector(stack);
	}

	/** single entry point for component-aware item rule-coverage keys. */
	Optional<String> itemRuleCoverageKey(ItemStack stack) {
		return EditorRuleCoverageKeys.itemKey(stack, () -> cachedExactSelector(stack));
	}

	Set<String> itemRuleCoverageKeys(List<ItemStack> stacks) {
		java.util.HashSet<String> keys = new java.util.HashSet<>(Math.max(16, stacks.size()));
		for (ItemStack stack : stacks) {
			itemRuleCoverageKey(stack).ifPresent(keys::add);
		}
		return keys;
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
		return core.buildPreviewDefinition(editId, editName, editEnabled, appearanceDraft, editPriority);
	}

	@Override
	public void setAppearanceDraft(AppearanceDraft appearanceDraft) {
		this.appearanceDraft = Objects.requireNonNull(appearanceDraft, "appearanceDraft");
	}

	@Override
	public void setEditPriority(int editPriority) {
		this.editPriority = editPriority;
	}

	@Override
	public AppearanceDraft appearanceDraft() {
		return appearanceDraft;
	}

	@Override
	public int editPriority() {
		return editPriority;
	}

	@Override
	public boolean editEnabled() {
		return editEnabled;
	}

	@Override
	public void setEditEnabled(boolean enabled) {
		this.editEnabled = enabled;
	}

	@Override
	public String editId() {
		return editId;
	}

	@Override
	public String pendingRawId() {
		String raw = core.pendingRawId(editId, editName);
		return raw == null ? "" : raw;
	}

	boolean canUseIndexedItemPreview() {
		return core.canUseIndexedItemPreview();
	}

	boolean canEditContents() {
		return core.canEditContents();
	}

	// rule-coverage sets, converged from the right-panel rebuild's single
	// EditorRuntimeServices.get().resolve* pass; queried by the source grid to flag rule-covered
	// cells (green visual, not toggleable) without reaching into the right panel.
	void updateRuleCoverage(Set<String> itemIds, Set<String> fluidIds, Set<String> genericKeys) {
		core.setCoveredSets(itemIds, fluidIds, genericKeys);
	}

	boolean isItemRuleCovered(String itemId) {
		return core.isItemRuleCovered(itemId);
	}

	boolean isFluidRuleCovered(String fluidId) {
		return core.isFluidRuleCovered(fluidId);
	}

	boolean isGenericRuleCovered(String genericKey) {
		return core.isGenericRuleCovered(genericKey);
	}

	boolean isWholeItemSelected(ItemStack stack) {
		return itemSelection.isWholeItemSelected(stack, contentsProjection().explicitItemSelectors());
	}

	boolean isExactSelected(ItemStack stack) {
		return itemSelection.isExactSelected(stack, contentsProjection().explicitItemSelectors());
	}

	void toggleSingleSelection(ItemStack stack) {
		mutateContentsDraft(next -> itemSelection.toggleSingleSelection(stack, next.explicitItemSelectors()));
	}

	boolean addSingleSelectionIfAbsent(ItemStack stack) {
		if (itemSelection.hasPreferredSelection(stack, contentsProjection().explicitItemSelectors())) {
			return false;
		}
		boolean[] changed = {false};
		mutateContentsDraft(next -> changed[0] = itemSelection.addSingleSelectionIfAbsent(
			stack, next.explicitItemSelectors()));
		return changed[0];
	}

	void toggleWholeItemSelection(ItemStack stack) {
		mutateContentsDraft(next -> itemSelection.toggleWholeItemSelection(stack, next.explicitItemSelectors()));
	}

	void removeSingleSelection(ItemStack stack, List<ItemStack> allItems) {
		mutateContentsDraft(next -> itemSelection.removeSingleSelection(
			stack, allItems, next.explicitItemSelectors()));
	}

	void removeAllSelectionsForItem(ItemStack stack) {
		mutateContentsDraft(next -> itemSelection.removeAllSelectionsForItem(stack, next.explicitItemSelectors()));
	}

	boolean isFluidSelected(EditorFluidIngredientView fluid) {
		return fluidSelection.isSelected(fluid, contentsProjection().fluidIds());
	}

	void toggleFluidSelection(EditorFluidIngredientView fluid) {
		mutateContentsDraft(next -> fluidSelection.toggleSelection(fluid, next.fluidIds()));
	}

	void addFluidId(String id) {
		if (fluidSelection.isIdSelected(id, contentsProjection().fluidIds())) {
			return;
		}
		mutateContentsDraft(next -> fluidSelection.addId(id, next.fluidIds()));
	}

	void removeFluidSelection(EditorFluidIngredientView fluid) {
		mutateContentsDraft(next -> fluidSelection.removeSelection(fluid, next.fluidIds()));
	}

	boolean isGenericSelected(EditorGenericIngredientView entry) {
		return genericSelection.isSelected(entry, contentsProjection().genericIds());
	}

	boolean isGenericTagMatched(EditorGenericIngredientView entry) {
		GroupFilterEditorDraft projection = contentsProjection();
		return genericSelection.isTagMatched(entry, projection.genericIds(), projection.genericTags());
	}

	void toggleGenericSelection(EditorGenericIngredientView entry) {
		mutateContentsDraft(next -> genericSelection.toggleSelection(entry, next.genericIds()));
	}

	void addGenericId(String typeId, String id) {
		if (genericSelection.containsId(typeId, id, contentsProjection().genericIds())) {
			return;
		}
		mutateContentsDraft(next -> genericSelection.addId(typeId, id, next.genericIds()));
	}

	void removeGenericSelection(EditorGenericIngredientView entry) {
		mutateContentsDraft(next -> genericSelection.removeSelection(entry, next.genericIds()));
	}

	Optional<GroupDefinition> trySave() {
		return core.trySave(editId, editName, editEnabled, nameTouched, appearanceDraft, editPriority);
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
	public GroupFilterRuleDraft.Node beginInsertRule(GroupFilterRuleDraft.NodeKind kind) {
		return core.beginInsertRule(kind);
	}

	@Override
	public boolean beginRuleEdit(GroupFilterRuleDraft.Node node) {
		return core.beginRuleEdit(node);
	}

	@Override
	public boolean hasRuleEditTransaction() {
		return core.hasRuleEditTransaction();
	}

	@Override
	public boolean ruleEditChanged() {
		return core.ruleEditChanged();
	}

	@Override
	public void commitRuleEdit() {
		core.commitRuleEdit();
	}

	@Override
	public void cancelRuleEdit() {
		core.cancelRuleEdit();
	}

	@Override
	public int unresolvedRuleCount() {
		var runtime = EditorRuntimeServices.ingredients();
		return (int) core.flattenedRuleNodes().stream().filter(flat ->
			EditorTagDiagnostics.warning(flat.node(), runtime) != null).count();
	}

	@Override
	public GroupFilterRuleDraft.Node wrapSelectedRule(GroupFilterRuleDraft.NodeKind kind) {
		return core.wrapSelectedRule(kind);
	}

	@Override
	public boolean canMoveRuleNode(GroupFilterRuleDraft.Node node, GroupFilterRuleDraft.Node targetParent) {
		return core.canMoveRuleNode(node, targetParent);
	}

	@Override
	public boolean moveRuleNode(GroupFilterRuleDraft.Node node, GroupFilterRuleDraft.Node targetParent, int index) {
		return core.moveRuleNode(node, targetParent, index);
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

	GroupFilterEditorDraft contentsDraftSnapshot() {
		return decodeContentsProjection().draft();
	}

	private GroupFilterEditorDraft contentsProjection() {
		if (contentsProjection == null) {
			refreshContentsProjection();
		}
		return contentsProjection;
	}

	private GroupFilterEditorDraft.DecodeResult decodeContentsProjection() {
		Optional<GroupFilter> filter = buildCurrentFilter();
		if (filter.isEmpty()) {
			boolean available = !core.hasRulesRoot();
			return new GroupFilterEditorDraft.DecodeResult(
				GroupFilterEditorDraft.empty(), available, List.of(), Set.of());
		}
		return GroupFilterEditorDraft.decode(filter.get());
	}

	private void refreshContentsProjection() {
        if (itemSelection != null) itemSelection.selectionChanged();
		GroupFilterEditorDraft.DecodeResult decoded = decodeContentsProjection();
		core.setContentsEditability(decoded.structurallyEditable(), decoded.flatIndexSafe());
		contentsProjection = decoded.draft();
	}

	private void mutateContentsDraft(Consumer<GroupFilterEditorDraft> mutation) {
		if (!core.canEditContents()) {
			return;
		}
		GroupFilterEditorDraft next = decodeContentsProjection().draft();
		Optional<GroupFilter> before = next.toFilter();
		mutation.accept(next);
		if (!next.toFilter().equals(before)) {
			core.syncRulesFromContentsDraft(next);
		}
	}

}
