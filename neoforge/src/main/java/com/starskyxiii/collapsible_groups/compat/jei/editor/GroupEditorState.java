package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.api.IngredientTypeRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.core.GroupFilterSummaryFormatter;
import com.starskyxiii.collapsible_groups.core.GroupFilterValidator;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupItemSelector;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds all mutable edit state for {@link GroupEditorScreen}.
 *
 * <p>The editor now tracks two parallel representations:
 * <ul>
 * <li>{@link #draft}: legacy flat selectors used by the contents tab and the
 *     indexed preview fast-path.</li>
 * <li>{@link #ruleDraft}: full filter tree used by the rules tab and persistence.</li>
 * </ul>
 */
final class GroupEditorState {
	private static final GroupFilter EMPTY_PREVIEW_FILTER = Filters.itemTag("minecraft:__cg_preview_empty__");

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

	// --- Rules draft ---
	final GroupFilterRuleDraft ruleDraft;
	private GroupFilterRuleDraft.Node selectedRuleNode;
	private boolean contentsQuickEditAvailable;

	/** Cache of exact stack selectors keyed by ItemStack identity. */
	private final IdentityHashMap<ItemStack, Optional<String>> exactSelectorCache = new IdentityHashMap<>();
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
		this.editGenericIds = draft.genericIds();
		this.editGenericTags = draft.genericTags();

		refreshContentsDraftFromRules();
		buildCurrentFilter()
			.filter(filter -> GroupFilterValidator.validate(filter).isEmpty())
			.ifPresent(filter -> lastValidPreviewFilter = filter);
	}

	Optional<String> cachedExactSelector(ItemStack stack) {
		return exactSelectorCache.computeIfAbsent(stack, GroupItemSelector::tryExactSelector);
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
		return new GroupDefinition(
			editId != null ? editId : "__preview__",
			editName,
			editEnabled,
			previewFilter
		);
	}

	boolean canUseIndexedItemPreview() {
		return contentsQuickEditAvailable;
	}

	boolean canEditContents() {
		return contentsQuickEditAvailable;
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
			syncRulesFromContentsDraft();
			return;
		}
		explicitSet.remove(GroupItemSelector.wholeItemSelector(stack));
		explicitSet.add(exactSelector);
		syncRulesFromContentsDraft();
	}

	void toggleWholeItemSelection(ItemStack stack) {
		String wholeItemSelector = GroupItemSelector.wholeItemSelector(stack);
		if (explicitSet.remove(wholeItemSelector)) {
			syncRulesFromContentsDraft();
			return;
		}
		removeExactSelectionsForItem(stack);
		explicitSet.add(wholeItemSelector);
		syncRulesFromContentsDraft();
	}

	void removeSingleSelection(ItemStack stack, List<ItemStack> allItems) {
		String exactSelector = GroupItemSelector.exactSelector(stack);
		if (explicitSet.remove(exactSelector)) {
			syncRulesFromContentsDraft();
			return;
		}
		String wholeItemSelector = GroupItemSelector.wholeItemSelector(stack);
		if (explicitSet.remove(wholeItemSelector)) {
			addAllSiblingVariantsExcept(stack, allItems);
			syncRulesFromContentsDraft();
		}
	}

	void removeAllSelectionsForItem(ItemStack stack) {
		Set<String> selectors = explicitSet.stream()
			.filter(selector -> GroupItemSelector.isSelectorForSameItem(selector, stack))
			.collect(Collectors.toSet());
		explicitSet.removeAll(selectors);
		syncRulesFromContentsDraft();
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
		String canonicalTypeId = canonicalTypeId(entry.typeId());
		return editGenericIds.stream().anyMatch(e ->
			e.ingredientType().equals(canonicalTypeId) && e.value().equals(entry.resourceId()));
	}

	boolean isGenericTagMatched(GenericIngredientView entry) {
		if (isGenericSelected(entry)) return false;
		String canonicalTypeId = canonicalTypeId(entry.typeId());
		return editGenericTags.stream().anyMatch(e ->
			e.ingredientType().equals(canonicalTypeId) && entry.tagIds().contains(e.value()));
	}

	void toggleGenericSelection(GenericIngredientView entry) {
		GroupFilterEditorDraft.GenericValue value = newGenericIdValue(entry.typeId(), entry.resourceId());
		if (!editGenericIds.remove(value)) {
			editGenericIds.add(value);
		}
		syncRulesFromContentsDraft();
	}

	void addGenericId(String typeId, String id) {
		GroupFilterEditorDraft.GenericValue value = newGenericIdValue(typeId, id);
		if (!editGenericIds.contains(value)) {
			editGenericIds.add(value);
			syncRulesFromContentsDraft();
		}
	}

	void removeGenericSelection(GenericIngredientView entry) {
		if (editGenericIds.remove(newGenericIdValue(entry.typeId(), entry.resourceId()))) {
			syncRulesFromContentsDraft();
		}
	}

	private static GroupFilterEditorDraft.GenericValue newGenericIdValue(String typeId, String value) {
		return new GroupFilterEditorDraft.GenericValue(canonicalTypeId(typeId), value);
	}

	private static String canonicalTypeId(String typeId) {
		String canonical = IngredientTypeRegistry.getCanonicalId(typeId);
		return canonical != null ? canonical : typeId;
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

	String filterSummary() {
		GroupFilter filter = buildCurrentFilter().orElse(null);
		if (filter == null) return Component.translatable(ModTranslationKeys.EDITOR_RULES_NO_FILTER).getString();
		return GroupFilterSummaryFormatter.format(filter);
	}

	String previewOwnershipNote() {
		return Component.translatable(ModTranslationKeys.EDITOR_PREVIEW_NOTE).getString();
	}

	String pendingIdLabel() {
		String id = currentOrGeneratedId();
		if (id == null || id.isBlank()) {
			return Component.translatable(ModTranslationKeys.EDITOR_PENDING_ID_GENERATING).getString();
		}
		if (existingDefinition != null) {
			return Component.translatable(ModTranslationKeys.EDITOR_PENDING_ID_EXISTING, id).getString();
		}
		String sanitized = GroupRegistry.sanitizeGeneratedIdBase(editName);
		if (!sanitized.isEmpty()) {
			return Component.translatable(ModTranslationKeys.EDITOR_PENDING_ID_ON_SAVE, id).getString();
		}
		return Component.translatable(ModTranslationKeys.EDITOR_PENDING_ID_ON_SAVE_GEN, id).getString();
	}

	List<GroupFilterRuleDraft.FlatNode> flattenedRuleNodes() {
		return ruleDraft.flatten();
	}

	GroupFilterRuleDraft.Node selectedRuleNode() {
		return selectedRuleNode;
	}

	void selectRuleNode(GroupFilterRuleDraft.Node node) {
		selectedRuleNode = node;
	}

	void ensureRuleSelection() {
		if (selectedRuleNode == null) {
			selectedRuleNode = ruleDraft.root();
		}
	}

	boolean canInsertRuleRelative() {
		return ruleDraft.canInsertRelativeTo(selectedRuleNode);
	}

	boolean canWrapSelectedRule(GroupFilterRuleDraft.NodeKind kind) {
		return ruleDraft.canWrap(selectedRuleNode, kind);
	}

	boolean canDeleteSelectedRule() {
		return selectedRuleNode != null;
	}

	GroupFilterRuleDraft.Node insertRuleRelative(GroupFilterRuleDraft.NodeKind kind) {
		GroupFilterRuleDraft.Node node = ruleDraft.insertRelativeTo(selectedRuleNode, kind);
		if (node != null) {
			selectedRuleNode = node;
			refreshContentsDraftFromRules();
		}
		return node;
	}

	GroupFilterRuleDraft.Node wrapSelectedRule(GroupFilterRuleDraft.NodeKind kind) {
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

	void deleteSelectedRule() {
		if (selectedRuleNode == null) {
			return;
		}
		selectedRuleNode = ruleDraft.delete(selectedRuleNode);
		if (selectedRuleNode == null) {
			selectedRuleNode = ruleDraft.root();
		}
		refreshContentsDraftFromRules();
	}

	void markRulesChanged() {
		refreshContentsDraftFromRules();
	}

	String contentsEditStatusLabel() {
		return Component.translatable(contentsQuickEditAvailable
			? ModTranslationKeys.EDITOR_FILTER_EDITABLE
			: ModTranslationKeys.EDITOR_FILTER_READONLY).getString();
	}

	List<Component> currentValidationErrors() {
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
		contentsQuickEditAvailable = decoded.structurallyEditable();
		if (contentsQuickEditAvailable) {
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

	private String currentOrGeneratedId() {
		if (editId != null && !editId.isEmpty()) {
			return editId;
		}
		if (editName == null || editName.isBlank()) {
			return null;
		}
		return GroupRegistry.generateUniqueId(editName);
	}
}
