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
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class EditorStateCore {
	private static final GroupFilter EMPTY_PREVIEW_FILTER = Filters.itemTag("minecraft:__cg_preview_empty__");

	private final GroupDefinition existingDefinition;
	private final GroupFilterRuleDraft ruleDraft;
	private final Runnable onRulesDraftChanged;

	private GroupFilterRuleDraft.Node selectedRuleNode;
	private boolean contentsQuickEditAvailable;
	private GroupFilter lastValidPreviewFilter = EMPTY_PREVIEW_FILTER;

	EditorStateCore(GroupDefinition existingDefinition, Runnable onRulesDraftChanged) {
		this.existingDefinition = existingDefinition;
		this.onRulesDraftChanged = Objects.requireNonNull(onRulesDraftChanged, "onRulesDraftChanged");
		this.ruleDraft = existingDefinition != null
			? GroupFilterRuleDraft.decode(existingDefinition.filter())
			: GroupFilterRuleDraft.empty();
		this.selectedRuleNode = ruleDraft.root();

		buildCurrentFilter()
			.filter(filter -> GroupFilterValidator.validate(filter).isEmpty())
			.ifPresent(filter -> lastValidPreviewFilter = filter);
	}

	Optional<GroupFilter> buildCurrentFilter() {
		return ruleDraft.toFilter();
	}

	GroupDefinition buildPreviewDefinition(String editId, String editName, boolean editEnabled) {
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

	void setContentsQuickEditAvailable(boolean contentsQuickEditAvailable) {
		this.contentsQuickEditAvailable = contentsQuickEditAvailable;
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

	Optional<GroupDefinition> trySave(String editId, String editName, boolean editEnabled) {
		if (!canSave(editName)) return Optional.empty();
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

	String filterSummary() {
		GroupFilter filter = buildCurrentFilter().orElse(null);
		if (filter == null) return Component.translatable(ModTranslationKeys.EDITOR_RULES_NO_FILTER).getString();
		return GroupFilterSummaryFormatter.format(filter);
	}

	String previewOwnershipNote() {
		return Component.translatable(ModTranslationKeys.EDITOR_PREVIEW_NOTE).getString();
	}

	String pendingIdLabel(String editId, String editName) {
		String id = currentOrGeneratedId(editId, editName);
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
			return editId;
		}
		if (editName == null || editName.isBlank()) {
			return null;
		}
		return GroupRegistry.generateUniqueId(editName);
	}
}
