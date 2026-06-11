package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import java.util.List;
import java.util.Objects;

public record GroupCardViewModel(
	String groupId,
	String groupName,
	GroupSource source,
	boolean enabled,
	GroupActionEligibility actionEligibility,
	PreviewPaneModel preview,
	boolean batchSelected
) {
	public GroupCardViewModel {
		groupId = requireNonBlank(groupId, "groupId");
		groupName = requireNonBlank(groupName, "groupName");
		source = source != null ? source : GroupSource.fromGroupId(groupId);
		actionEligibility = actionEligibility != null
			? actionEligibility
			: GroupActionEligibility.forSource(source);
		preview = preview != null
			? preview
			: PreviewPaneModel.empty(groupId, groupName, enabled);
	}

	public static GroupCardViewModel of(
		String groupId,
		String groupName,
		boolean enabled,
		int itemCount,
		int fluidCount,
		int genericCount,
		List<PreviewIngredientModel> previewEntries
	) {
		GroupSource source = GroupSource.fromGroupId(groupId);
		PreviewPaneModel preview = new PreviewPaneModel(
			groupId,
			groupName,
			source,
			enabled,
			itemCount,
			fluidCount,
			genericCount,
			previewEntries
		);
		return new GroupCardViewModel(
			groupId,
			groupName,
			source,
			enabled,
			GroupActionEligibility.forSource(source),
			preview,
			false
		);
	}

	public GroupCardViewModel withBatchSelected(boolean selected) {
		return new GroupCardViewModel(
			groupId,
			groupName,
			source,
			enabled,
			actionEligibility,
			preview,
			selected
		);
	}

	private static String requireNonBlank(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value.trim();
	}
}
