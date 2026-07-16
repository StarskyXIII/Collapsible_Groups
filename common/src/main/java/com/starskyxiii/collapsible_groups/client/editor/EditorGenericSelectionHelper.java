package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;

import java.util.List;
import java.util.Objects;

final class EditorGenericSelectionHelper {
	private final List<GroupFilterEditorDraft.GenericValue> genericIds;
	private final List<GroupFilterEditorDraft.GenericValue> genericTags;
	private final Runnable onContentsDraftChanged;

	EditorGenericSelectionHelper(
		List<GroupFilterEditorDraft.GenericValue> genericIds,
		List<GroupFilterEditorDraft.GenericValue> genericTags,
		Runnable onContentsDraftChanged
	) {
		this.genericIds = Objects.requireNonNull(genericIds, "genericIds");
		this.genericTags = Objects.requireNonNull(genericTags, "genericTags");
		this.onContentsDraftChanged = Objects.requireNonNull(onContentsDraftChanged, "onContentsDraftChanged");
	}

	boolean isSelected(EditorGenericIngredientView entry) {
		String canonicalTypeId = canonicalTypeId(entry.typeId());
		return genericIds.stream().anyMatch(value ->
			sameType(value.ingredientType(), canonicalTypeId) && value.value().equals(entry.resourceId()));
	}

	boolean isTagMatched(EditorGenericIngredientView entry) {
		if (isSelected(entry)) return false;
		String canonicalTypeId = canonicalTypeId(entry.typeId());
		return genericTags.stream().anyMatch(value ->
			sameType(value.ingredientType(), canonicalTypeId) && entry.tagIds().contains(value.value()));
	}

	void toggleSelection(EditorGenericIngredientView entry) {
		if (!removeMatchingId(entry.typeId(), entry.resourceId())) {
			genericIds.add(newGenericIdValue(entry.typeId(), entry.resourceId()));
		}
		onContentsDraftChanged.run();
	}

	void addId(String typeId, String id) {
		if (!containsMatchingId(typeId, id)) {
			genericIds.add(newGenericIdValue(typeId, id));
			onContentsDraftChanged.run();
		}
	}

	void removeSelection(EditorGenericIngredientView entry) {
		if (removeMatchingId(entry.typeId(), entry.resourceId())) {
			onContentsDraftChanged.run();
		}
	}

	private boolean containsMatchingId(String typeId, String id) {
		String canonicalTypeId = canonicalTypeId(typeId);
		return genericIds.stream().anyMatch(value ->
			sameType(value.ingredientType(), canonicalTypeId) && value.value().equals(id));
	}

	private boolean removeMatchingId(String typeId, String id) {
		String canonicalTypeId = canonicalTypeId(typeId);
		return genericIds.removeIf(value ->
			sameType(value.ingredientType(), canonicalTypeId) && value.value().equals(id));
	}

	private static GroupFilterEditorDraft.GenericValue newGenericIdValue(String typeId, String value) {
		return new GroupFilterEditorDraft.GenericValue(canonicalTypeId(typeId), value);
	}

	private static boolean sameType(String rawTypeId, String canonicalTypeId) {
		return canonicalTypeId(rawTypeId).equals(canonicalTypeId);
	}

	private static String canonicalTypeId(String typeId) {
		String canonical = IngredientTypeIds.getCanonicalId(typeId);
		return canonical != null ? canonical : typeId;
	}
}
