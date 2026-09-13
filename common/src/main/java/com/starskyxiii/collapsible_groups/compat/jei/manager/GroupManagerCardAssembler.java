package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupPreviewSnapshot;
import com.starskyxiii.collapsible_groups.viewer.ViewerPreviewValue;

import java.util.ArrayList;
import java.util.List;

/** Builds manager cards exclusively from one active viewer index generation. */
final class GroupManagerCardAssembler {
	private GroupManagerCardAssembler() {}

	static Result build(com.starskyxiii.collapsible_groups.group.GroupRepository.ReadSnapshot repository,
		com.starskyxiii.collapsible_groups.viewer.ViewerGroupDisplaySnapshot display) {
		List<GroupManagerCard> cards = new ArrayList<>(repository.groups().size());
		int totalItems = 0;
		int totalFluids = 0;
		int totalGeneric = 0;
		for (GroupDefinition group : repository.groups()) {
			var evaluation = display.evaluation(group);
			var snapshot = display.preview(group).orElseGet(GroupManagerCardAssembler::emptySnapshot);
			totalItems += evaluation.itemCount();
			totalFluids += evaluation.fluidCount();
			totalGeneric += evaluation.genericCount();
			cards.add(GroupManagerCard.create(group,
				com.starskyxiii.collapsible_groups.client.manager.model.GroupSource.from(repository.winningSources().get(group.id())),
				evaluation, previewEntries(snapshot.allValues())));
		}
		return new Result(cards, display.pending(), totalItems, totalFluids, totalGeneric);
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
