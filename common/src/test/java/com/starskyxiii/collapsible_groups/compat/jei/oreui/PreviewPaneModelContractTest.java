package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewPaneModelContractTest {
	@Test
	void previewPaneCopiesEntriesAndExposesCountSummary() {
		List<PreviewIngredientModel> entries = new ArrayList<>();
		entries.add(PreviewIngredientModel.item("minecraft:stone"));
		entries.add(PreviewIngredientModel.fluid("minecraft:water"));

		PreviewPaneModel model = new PreviewPaneModel(
			"custom_group",
			"Custom Group",
			null,
			true,
			2,
			1,
			3,
			entries
		);
		entries.add(PreviewIngredientModel.generic("mod:custom"));

		assertEquals(GroupSource.USER, model.source());
		assertEquals(6, model.totalCount());
		assertEquals(2, model.shownEntryCount());
		assertEquals(4, model.hiddenEntryCount());
		assertEquals(List.of(
			new PreviewPaneModel.CountSummaryPart(PreviewIngredientKind.ITEM, 2),
			new PreviewPaneModel.CountSummaryPart(PreviewIngredientKind.FLUID, 1),
			new PreviewPaneModel.CountSummaryPart(PreviewIngredientKind.GENERIC, 3)
		), model.countSummaryParts());
		assertThrows(UnsupportedOperationException.class, () -> model.entries().add(PreviewIngredientModel.item("minecraft:dirt")));
	}

	@Test
	void previewIngredientModelNormalizesDisplayAndOwnerNames() {
		PreviewIngredientModel model = new PreviewIngredientModel(
			PreviewIngredientKind.GENERIC,
			" mod:thing ",
			" Display Name ",
			List.of("Owner A", " ", "Owner B")
		);

		assertEquals("mod:thing", model.id());
		assertEquals("Display Name", model.displayName());
		assertEquals(List.of("Owner A", "Owner B"), model.ownerGroupNames());
		assertTrue(model.hasOwnerGroups());
	}
}
