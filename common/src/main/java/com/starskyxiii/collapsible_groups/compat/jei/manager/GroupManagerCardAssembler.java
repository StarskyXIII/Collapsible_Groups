package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupPreviewSnapshot;
import com.starskyxiii.collapsible_groups.viewer.ViewerPreviewValue;

import java.util.ArrayList;
import java.util.List;

/** Builds manager cards exclusively from one active viewer index generation. */
final class GroupManagerCardAssembler {
	private GroupManagerCardAssembler() {}

	static Result build(List<GroupDefinition> groups, ViewerGroupIndex index) {
		boolean generationPending = index.candidates().isEmpty();
		List<GroupManagerCard> cards = new ArrayList<>(groups.size());
		int totalItems = 0;
		int totalFluids = 0;
		int totalGeneric = 0;
		for (GroupDefinition group : groups) {
			ViewerGroupPreviewSnapshot snapshot = generationPending
				? emptySnapshot()
				: index.fullMatchSnapshot(group).orElseGet(GroupManagerCardAssembler::emptySnapshot);
			totalItems += snapshot.items().size();
			totalFluids += snapshot.fluids().size();
			totalGeneric += snapshot.generic().size();
			cards.add(GroupManagerCard.create(group, snapshot.items().size(), snapshot.fluids().size(),
				snapshot.generic().size(), previewEntries(snapshot.allValues())));
		}
		return new Result(cards, generationPending, totalItems, totalFluids, totalGeneric);
	}

	/** Takes mutable ownership of an assembler or pending snapshot for screen-local state changes. */
	static List<GroupManagerCard> mutableWorkingCopy(List<GroupManagerCard> snapshot) {
		return new ArrayList<>(snapshot);
	}

	private static ViewerGroupPreviewSnapshot emptySnapshot() {
		return new ViewerGroupPreviewSnapshot(List.of(), List.of(), List.of());
	}

	private static List<GroupPreviewEntry> previewEntries(List<ViewerPreviewValue> values) {
		return values.stream().map(value -> value.itemStack() != null
			? GroupPreviewEntry.ofItem(value.itemStack())
			: GroupPreviewEntry.ofRenderer(value.renderer()::render)).toList();
	}

	record Result(List<GroupManagerCard> cards, boolean generationPending,
		int totalItems, int totalFluids, int totalGeneric) {
		Result {
			cards = List.copyOf(cards);
		}
	}
}
