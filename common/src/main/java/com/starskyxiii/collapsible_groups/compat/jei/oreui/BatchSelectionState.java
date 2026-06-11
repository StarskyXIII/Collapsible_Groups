package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record BatchSelectionState(List<String> selectedGroupIds) {
	public BatchSelectionState {
		selectedGroupIds = copyDistinct(selectedGroupIds);
	}

	public static BatchSelectionState empty() {
		return new BatchSelectionState(List.of());
	}

	public int selectedCount() {
		return selectedGroupIds.size();
	}

	public boolean isSelected(String groupId) {
		return selectedGroupIds.contains(Objects.requireNonNull(groupId, "groupId"));
	}

	public BatchSelectionState select(String groupId) {
		Objects.requireNonNull(groupId, "groupId");
		if (isSelected(groupId)) {
			return this;
		}
		List<String> next = new ArrayList<>(selectedGroupIds);
		next.add(groupId);
		return new BatchSelectionState(next);
	}

	public BatchSelectionState deselect(String groupId) {
		Objects.requireNonNull(groupId, "groupId");
		if (!isSelected(groupId)) {
			return this;
		}
		List<String> next = new ArrayList<>(selectedGroupIds);
		next.remove(groupId);
		return new BatchSelectionState(next);
	}

	public BatchSelectionState toggle(String groupId) {
		return isSelected(groupId) ? deselect(groupId) : select(groupId);
	}

	public BatchSelectionState clear() {
		return selectedGroupIds.isEmpty() ? this : empty();
	}

	private static List<String> copyDistinct(List<String> groupIds) {
		Objects.requireNonNull(groupIds, "groupIds");
		LinkedHashSet<String> distinct = new LinkedHashSet<>();
		for (String groupId : groupIds) {
			distinct.add(Objects.requireNonNull(groupId, "groupId"));
		}
		return List.copyOf(distinct);
	}
}
