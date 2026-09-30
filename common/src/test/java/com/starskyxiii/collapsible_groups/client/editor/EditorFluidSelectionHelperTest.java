package com.starskyxiii.collapsible_groups.client.editor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchDocument;
import net.minecraft.network.chat.Component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorFluidSelectionHelperTest {
	@Test
	void detectsSelectedIds() {
		EditorFluidSelectionHelper helper = new EditorFluidSelectionHelper();
		List<String> ids = new ArrayList<>(List.of("minecraft:water"));

		assertTrue(helper.isIdSelected("minecraft:water", ids));
		assertFalse(helper.isIdSelected("minecraft:lava", ids));
	}

	@Test
	void toggleIdAddsAndRemoves() {
		List<String> ids = new ArrayList<>();
		EditorFluidSelectionHelper helper = new EditorFluidSelectionHelper();

		helper.toggleId("minecraft:water", ids);
		assertEquals(List.of("minecraft:water"), ids);

		helper.toggleId("minecraft:water", ids);
		assertEquals(List.of(), ids);
	}

	@Test
	void addIdAppendsAndIgnoresDuplicates() {
		List<String> ids = new ArrayList<>(List.of("minecraft:water"));
		EditorFluidSelectionHelper helper = new EditorFluidSelectionHelper();

		assertTrue(helper.addId("minecraft:lava", ids));
		assertFalse(helper.addId("minecraft:water", ids));

		assertEquals(List.of("minecraft:water", "minecraft:lava"), ids);
	}

	@Test
	void removeIdReportsWhetherTheValueWasPresent() {
		List<String> ids = new ArrayList<>(List.of("minecraft:water"));
		EditorFluidSelectionHelper helper = new EditorFluidSelectionHelper();

		assertFalse(helper.removeId("minecraft:lava", ids));
		assertEquals(List.of("minecraft:water"), ids);

		assertTrue(helper.removeId("minecraft:water", ids));
		assertEquals(List.of(), ids);
	}

	@Test
	void removeIdRemovesOnlyFirstDuplicate() {
		List<String> ids = new ArrayList<>(List.of("minecraft:water", "minecraft:water"));
		EditorFluidSelectionHelper helper = new EditorFluidSelectionHelper();

		assertTrue(helper.removeId("minecraft:water", ids));

		assertEquals(List.of("minecraft:water"), ids);
	}

	@Test
	void viewSelectionRoundTripsThroughResourceIdWithoutInspectingTheOpaqueIngredient() {
		List<String> ids = new ArrayList<>();
		EditorFluidSelectionHelper helper = new EditorFluidSelectionHelper();
		EditorFluidIngredientView view = new EditorFluidIngredientView(new Object(), Component.literal("Water"),
			"minecraft:water", IngredientSearchDocument.of(List.of(), List.of(), List.of()), null);

		helper.toggleSelection(view, ids);
		assertTrue(helper.isSelected(view, ids));
		assertEquals(List.of("minecraft:water"), ids);
		assertTrue(helper.removeSelection(view, ids));
		assertFalse(helper.isSelected(view, ids));
	}
}
