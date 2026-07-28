package com.starskyxiii.collapsible_groups.compat.jei.manager;


import com.starskyxiii.collapsible_groups.client.manager.model.GroupActionEligibility;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupCardViewModel;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupSource;
import com.starskyxiii.collapsible_groups.client.preview.model.PreviewPaneModel;
import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import java.util.List;
import java.util.Objects;

public record GroupManagerCard(
	GroupDefinition group,
	List<GroupPreviewEntry> previewEntries,
	List<GroupPreviewEntry> headerEntries,
	int itemCount,
	int fluidCount,
	int genericCount,
	GroupCardViewModel viewModel
) {
	public GroupManagerCard {
		group = Objects.requireNonNull(group, "group");
		previewEntries = List.copyOf(Objects.requireNonNull(previewEntries, "previewEntries"));
		headerEntries = List.copyOf(Objects.requireNonNull(headerEntries, "headerEntries"));
		if (itemCount < 0 || fluidCount < 0 || genericCount < 0) {
			throw new IllegalArgumentException("preview counts must not be negative");
		}
		viewModel = viewModel != null ? viewModel : buildViewModel(group, itemCount, fluidCount, genericCount);
	}

	public static GroupManagerCard create(
		GroupDefinition group,
		int itemCount,
		int fluidCount,
		int genericCount,
		List<GroupPreviewEntry> previewEntries
	) {
		List<GroupPreviewEntry> headers = previewEntries.stream().limit(2).toList();
		return new GroupManagerCard(group, previewEntries, headers,
			itemCount, fluidCount, genericCount, null);
	}

	public static GroupManagerCard create(
		GroupDefinition group,
		int itemCount,
		int fluidCount,
		int genericCount,
		List<GroupPreviewEntry> previewEntries,
		List<GroupPreviewEntry> headerEntries
	) {
		return new GroupManagerCard(group, previewEntries, headerEntries,
			itemCount, fluidCount, genericCount, null);
	}

	public String id() {
		return group.id();
	}

	public String displayName() {
		return resolvedDisplayName(group);
	}

	public GroupSource source() {
		return viewModel.source();
	}

	public GroupActionEligibility actionEligibility() {
		return viewModel.actionEligibility();
	}

	public boolean editable() {
		return source().userEditable();
	}

	public int entryCount() {
		return previewEntries.size();
	}

	/**
	 * Header entries are viewer-neutral render values assembled by the active viewer path.
	 */
	public List<GroupPreviewEntry> headerSource() {
		return headerEntries;
	}

	public GroupManagerCard withGroup(GroupDefinition updatedGroup) {
		Objects.requireNonNull(updatedGroup, "updatedGroup");
		return new GroupManagerCard(
			updatedGroup,
			previewEntries,
			headerEntries,
			itemCount,
			fluidCount,
			genericCount,
			buildViewModel(updatedGroup, itemCount(), fluidCount(), genericCount())
		);
	}

	private static GroupCardViewModel buildViewModel(
		GroupDefinition group,
		int itemCount,
		int fluidCount,
		int genericCount
	) {
		GroupSource source = GroupSource.fromGroupId(group.id());
		String displayName = resolvedDisplayName(group);
		PreviewPaneModel preview = new PreviewPaneModel(
			group.id(),
			displayName,
			source,
			group.enabled(),
			itemCount,
			fluidCount,
			genericCount,
			List.of()
		);
		return new GroupCardViewModel(
			group.id(),
			displayName,
			source,
			group.enabled(),
			GroupActionEligibility.forSource(source),
			preview,
			false
		);
	}

	private static String resolvedDisplayName(GroupDefinition group) {
		String resolved = group.displayName().fallback();
		return resolved.isEmpty() ? group.id() : resolved;
	}
}
