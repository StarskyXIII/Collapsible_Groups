package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;

import java.util.List;

final class EditorGenericSelectionHelper {
	boolean isSelected(EditorGenericIngredientView entry,
		List<GroupFilterEditorDraft.GenericValue> genericIds) {
		String canonicalTypeId = canonicalTypeId(entry.typeId());
		return genericIds.stream().anyMatch(value ->
			sameType(value.ingredientType(), canonicalTypeId) && value.value().equals(entry.resourceId()));
	}

	boolean isTagMatched(EditorGenericIngredientView entry,
		List<GroupFilterEditorDraft.GenericValue> genericIds,
		List<GroupFilterEditorDraft.GenericValue> genericTags) {
		if (isSelected(entry, genericIds)) return false;
		String canonicalTypeId = canonicalTypeId(entry.typeId());
		return genericTags.stream().anyMatch(value ->
			sameType(value.ingredientType(), canonicalTypeId) && entry.tagIds().contains(value.value()));
	}

	void toggleSelection(EditorGenericIngredientView entry,
		List<GroupFilterEditorDraft.GenericValue> genericIds) {
		if (!removeMatchingId(entry.typeId(), entry.resourceId(), genericIds)) {
			genericIds.add(newGenericIdValue(entry.typeId(), entry.resourceId()));
		}
	}

	boolean addId(String typeId, String id, List<GroupFilterEditorDraft.GenericValue> genericIds) {
		if (!containsMatchingId(typeId, id, genericIds)) {
			genericIds.add(newGenericIdValue(typeId, id));
			return true;
		}
		return false;
	}

	boolean containsId(String typeId, String id, List<GroupFilterEditorDraft.GenericValue> genericIds) {
		return containsMatchingId(typeId, id, genericIds);
	}

	boolean removeSelection(EditorGenericIngredientView entry,
		List<GroupFilterEditorDraft.GenericValue> genericIds) {
		return removeMatchingId(entry.typeId(), entry.resourceId(), genericIds);
	}

	private boolean containsMatchingId(String typeId, String id,
		List<GroupFilterEditorDraft.GenericValue> genericIds) {
		String canonicalTypeId = canonicalTypeId(typeId);
		return genericIds.stream().anyMatch(value ->
			sameType(value.ingredientType(), canonicalTypeId) && value.value().equals(id));
	}

	private boolean removeMatchingId(String typeId, String id,
		List<GroupFilterEditorDraft.GenericValue> genericIds) {
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
