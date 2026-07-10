package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.oreui.RuleTagResolution;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.AppearanceDraft;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.core.GroupFilterValidator;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class EditorStateCore {
	private static final GroupFilter EMPTY_PREVIEW_FILTER = Filters.itemTag("minecraft:__cg_preview_empty__");

	private final GroupDefinition existingDefinition;
	private final GroupFilterRuleDraft ruleDraft;
	private final Runnable onRulesDraftChanged;
	private final boolean saveAsNew;
	@Nullable
	private final String sourceGroupId;

	private GroupFilterRuleDraft.Node selectedRuleNode;
	private GroupFilterRuleDraft.Node pendingRuleNode;
	private boolean contentsQuickEditAvailable;
	// decoupled from contentsQuickEditAvailable. A hybrid draft (preserved advanced
	// subtrees present) is still contents-editable but is NOT flat-index safe, so the
	// indexed item preview must not be used for it — see canUseIndexedItemPreview().
	private boolean flatIndexPreviewSafe;
	private GroupFilter lastValidPreviewFilter = EMPTY_PREVIEW_FILTER;

	// id sets of everything the current group's rules fully match, keyed the
	// same way the source-grid ownership caches are (item registry id, fluid
	// resource id, "typeId|resourceId" for generic). Converged here from the single
	// GroupRegistry.resolve* pass shared with the right-panel rebuild, so the source
	// grid can flag rule-covered cells without re-resolving or reaching into the
	// right panel. Rebuilt on every contents/rules draft change.
	private Set<String> coveredItemIds = Set.of();
	private Set<String> coveredFluidIds = Set.of();
	private Set<String> coveredGenericKeys = Set.of();

	EditorStateCore(GroupDefinition existingDefinition, Runnable onRulesDraftChanged) {
		this(existingDefinition, false, null, onRulesDraftChanged);
	}

	EditorStateCore(GroupDefinition existingDefinition, boolean saveAsNew, Runnable onRulesDraftChanged) {
		this(existingDefinition, saveAsNew, null, onRulesDraftChanged);
	}

	EditorStateCore(
		GroupDefinition existingDefinition,
		boolean saveAsNew,
		@Nullable String sourceGroupId,
		Runnable onRulesDraftChanged
	) {
		this.existingDefinition = existingDefinition;
		this.saveAsNew = saveAsNew;
		this.sourceGroupId = normalizeSourceGroupId(sourceGroupId);
		this.onRulesDraftChanged = Objects.requireNonNull(onRulesDraftChanged, "onRulesDraftChanged");
		this.ruleDraft = existingDefinition != null
			? GroupFilterRuleDraft.decode(existingDefinition.filter())
			: GroupFilterRuleDraft.empty();
		this.selectedRuleNode = ruleDraft.root();

		buildCurrentFilter()
			.filter(filter -> GroupFilterValidator.validate(filter).isEmpty())
			.ifPresent(filter -> lastValidPreviewFilter = filter);
	}

	private static @Nullable String normalizeSourceGroupId(@Nullable String sourceGroupId) {
		return sourceGroupId == null || sourceGroupId.isBlank() ? null : sourceGroupId;
	}

	boolean saveAsNew() {
		return saveAsNew;
	}

	@Nullable
	String sourceGroupId() {
		return sourceGroupId;
	}

	Optional<GroupFilter> buildCurrentFilter() {
		return ruleDraft.toFilter();
	}

	GroupDefinition buildPreviewDefinition(String editId, String editName, boolean editEnabled) {
		AppearanceDraft appearance = existingDefinition != null
			? AppearanceDraft.from(existingDefinition)
			: AppearanceDraft.fromIconIds(List.of(), com.starskyxiii.collapsible_groups.core.GroupTheme.EMPTY);
		int priority = existingDefinition != null ? existingDefinition.priority() : 0;
		return buildPreviewDefinition(editId, editName, editEnabled, appearance, priority);
	}

	GroupDefinition buildPreviewDefinition(
		String editId,
		String editName,
		boolean editEnabled,
		AppearanceDraft appearance,
		int priority
	) {
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
			existingDefinition,
			appearance,
			priority
		);
	}

	boolean canUseIndexedItemPreview() {
		return flatIndexPreviewSafe;
	}

	boolean canEditContents() {
		return contentsQuickEditAvailable;
	}

	/**
	 * sets contents editability and flat-index preview safety independently.
	 * A hybrid draft is {@code editable=true} but {@code flatIndexSafe=false}.
	 */
	void setContentsEditability(boolean editable, boolean flatIndexSafe) {
		this.contentsQuickEditAvailable = editable;
		this.flatIndexPreviewSafe = flatIndexSafe;
	}

	void setContentsQuickEditAvailable(boolean contentsQuickEditAvailable) {
		setContentsEditability(contentsQuickEditAvailable, contentsQuickEditAvailable);
	}

	/**
	 * Stores the id sets of everything the current group's rules fully match,
	 * shared from the right-panel rebuild's single resolve pass. Defensive
	 * copies; nulls become empty sets.
	 */
	void setCoveredSets(Set<String> itemIds, Set<String> fluidIds, Set<String> genericKeys) {
		this.coveredItemIds = itemIds == null ? Set.of() : Set.copyOf(itemIds);
		this.coveredFluidIds = fluidIds == null ? Set.of() : Set.copyOf(fluidIds);
		this.coveredGenericKeys = genericKeys == null ? Set.of() : Set.copyOf(genericKeys);
	}

	boolean isItemRuleCovered(String itemId) {
		return itemId != null && coveredItemIds.contains(itemId);
	}

	boolean isFluidRuleCovered(String fluidId) {
		return fluidId != null && coveredFluidIds.contains(fluidId);
	}

	boolean isGenericRuleCovered(String genericKey) {
		return genericKey != null && coveredGenericKeys.contains(genericKey);
	}

	boolean hasRulesRoot() {
		return ruleDraft.hasRoot();
	}

	void syncRulesFromContentsDraft(GroupFilterEditorDraft draft) {
		if (!contentsQuickEditAvailable) {
			return;
		}
		GroupFilterRuleDraft replacement = draft.toFilter()
			.map(GroupFilterRuleDraft::decode)
			.orElseGet(GroupFilterRuleDraft::empty);
		ruleDraft.replaceWith(replacement);
		selectedRuleNode = ruleDraft.root();
	}

	Optional<GroupDefinition> trySave(String editId, String editName, boolean editEnabled, boolean nameTouched) {
		AppearanceDraft appearance = existingDefinition != null
			? AppearanceDraft.from(existingDefinition)
			: AppearanceDraft.fromIconIds(List.of(), com.starskyxiii.collapsible_groups.core.GroupTheme.EMPTY);
		int priority = existingDefinition != null ? existingDefinition.priority() : 0;
		return trySave(editId, editName, editEnabled, nameTouched, appearance, priority);
	}

	Optional<GroupDefinition> trySave(
		String editId,
		String editName,
		boolean editEnabled,
		boolean nameTouched,
		AppearanceDraft appearance,
		int priority
	) {
		if (!canSave(editName)) return Optional.empty();
		Optional<GroupFilter> filter = buildCurrentFilter();
		String id = idForSave(editId, editName);
		try {
			GroupDefinition saved = shouldPreserveDisplayName(id, nameTouched)
				? GroupEditorDefinitionFactory.createWithDisplayName(id, existingDefinition.displayName(), editEnabled,
					filter.get(), existingDefinition, appearance, priority)
				: GroupEditorDefinitionFactory.create(id, editName, editEnabled, filter.get(), existingDefinition,
					appearance, priority);
			GroupRegistry.saveQuietly(saved);
			return Optional.of(saved);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private boolean shouldPreserveDisplayName(String id, boolean nameTouched) {
		return !nameTouched
			&& !saveAsNew
			&& existingDefinition != null
			&& existingDefinition.id().equals(id);
	}

	boolean canSave(String editName) {
		return !(editName == null || editName.isBlank())
			&& buildCurrentFilter().isPresent()
			&& currentValidationErrors().isEmpty();
	}

	List<Component> saveBlockedTooltip(String editName) {
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

	String previewOwnershipNote() {
		return Component.translatable(ModTranslationKeys.EDITOR_PREVIEW_NOTE).getString();
	}

	String pendingIdLabel(String editId, String editName) {
		String id = currentOrGeneratedId(editId, editName);
		if (id == null || id.isBlank()) {
			return Component.translatable(ModTranslationKeys.EDITOR_PENDING_ID_GENERATING).getString();
		}
		if (existingDefinition != null && !saveAsNew) {
			return Component.translatable(ModTranslationKeys.EDITOR_PENDING_ID_EXISTING, id).getString();
		}
		String sanitized = GroupRegistry.sanitizeGeneratedIdBase(editName);
		if (!sanitized.isEmpty()) {
			return Component.translatable(ModTranslationKeys.EDITOR_PENDING_ID_ON_SAVE, id).getString();
		}
		return Component.translatable(ModTranslationKeys.EDITOR_PENDING_ID_ON_SAVE_GEN, id).getString();
	}

	@Nullable
	String pendingRawId(String editId, String editName) {
		return currentOrGeneratedId(editId, editName);
	}

	String contentsEditStatusLabel() {
		return Component.translatable(contentsQuickEditAvailable
			? ModTranslationKeys.EDITOR_FILTER_EDITABLE
			: ModTranslationKeys.EDITOR_FILTER_READONLY).getString();
	}

	List<GroupFilterRuleDraft.FlatNode> flattenedRuleNodes() {
		return ruleDraft.flatten();
	}

	@Nullable
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

	@Nullable
	GroupFilterRuleDraft.Node insertRuleRelative(GroupFilterRuleDraft.NodeKind kind) {
		GroupFilterRuleDraft.Node node = ruleDraft.insertRelativeTo(selectedRuleNode, kind);
		if (node != null) {
			selectedRuleNode = node;
			onRulesDraftChanged.run();
		}
		return node;
	}

	@Nullable
	GroupFilterRuleDraft.Node insertRuleRelativePending(GroupFilterRuleDraft.NodeKind kind) {
		GroupFilterRuleDraft.Node node = insertRuleRelative(kind);
		if (node != null) {
			pendingRuleNode = node;
		}
		return node;
	}

	boolean hasPendingRuleNode() {
		return pendingRuleNode != null;
	}

	void commitPendingRuleNode() {
		pendingRuleNode = null;
	}

	void cancelPendingRuleNode() {
		if (pendingRuleNode == null) {
			return;
		}
		selectedRuleNode = pendingRuleNode;
		pendingRuleNode = null;
		deleteSelectedRule();
	}

	int unresolvedRuleCount(RuleTagResolution.TagExistenceLookup lookup) {
		return RuleTagResolution.countUnresolved(ruleDraft.flatten(), lookup);
	}

	@Nullable
	GroupFilterRuleDraft.Node wrapSelectedRule(GroupFilterRuleDraft.NodeKind kind) {
		if (selectedRuleNode == null) {
			return null;
		}
		GroupFilterRuleDraft.Node node = ruleDraft.wrap(selectedRuleNode, kind);
		if (node != null) {
			selectedRuleNode = node;
			onRulesDraftChanged.run();
		}
		return node;
	}

	boolean canMoveRuleNode(GroupFilterRuleDraft.Node node, GroupFilterRuleDraft.Node targetParent) {
		return ruleDraft.canMove(node, targetParent);
	}

	boolean moveRuleNode(GroupFilterRuleDraft.Node node, GroupFilterRuleDraft.Node targetParent, int index) {
		if (!ruleDraft.moveNode(node, targetParent, index)) {
			return false;
		}
		selectedRuleNode = node;
		onRulesDraftChanged.run();
		return true;
	}

	void deleteSelectedRule() {
		if (selectedRuleNode == null) {
			return;
		}
		selectedRuleNode = ruleDraft.delete(selectedRuleNode);
		if (selectedRuleNode == null) {
			selectedRuleNode = ruleDraft.root();
		}
		onRulesDraftChanged.run();
	}

	void markRulesChanged() {
		onRulesDraftChanged.run();
	}

	List<Component> currentValidationErrors() {
		return buildCurrentFilter()
			.map(GroupFilterValidator::validateComponents)
			.orElse(List.of());
	}

	private String currentOrGeneratedId(String editId, String editName) {
		if (editId != null && !editId.isEmpty()) {
			if (saveAsNew && GroupRegistry.findById(editId).isPresent()) {
				return GroupRegistry.generateUniqueIdIncludingKubeJs(editName);
			}
			return editId;
		}
		if (editName == null || editName.isBlank()) {
			return null;
		}
		return GroupRegistry.generateUniqueId(editName);
	}

	private String idForSave(String editId, String editName) {
		if (editId != null && !editId.isEmpty()) {
			if (!saveAsNew || GroupRegistry.findById(editId).isEmpty()) {
				return editId;
			}
		}
		return saveAsNew ? GroupRegistry.generateUniqueIdIncludingKubeJs(editName) : GroupRegistry.generateUniqueId(editName);
	}
}
