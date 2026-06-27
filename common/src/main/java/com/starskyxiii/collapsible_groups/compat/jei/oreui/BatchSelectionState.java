package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

	public BatchSelectionState pruneTo(Collection<String> allowedGroupIds) {
		Objects.requireNonNull(allowedGroupIds, "allowedGroupIds");
		if (selectedGroupIds.isEmpty()) {
			return this;
		}
		Set<String> allowed = new HashSet<>(allowedGroupIds);
		List<String> next = new ArrayList<>();
		for (String groupId : selectedGroupIds) {
			if (allowed.contains(groupId)) {
				next.add(groupId);
			}
		}
		return next.size() == selectedGroupIds.size() ? this : new BatchSelectionState(next);
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
