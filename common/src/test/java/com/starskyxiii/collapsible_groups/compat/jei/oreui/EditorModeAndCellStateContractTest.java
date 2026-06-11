package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorModeAndCellStateContractTest {
	@Test
	void sourceModesExposeSourceGridControls() {
		assertSourceMode(EditorMode.ITEMS, PreviewIngredientKind.ITEM);
		assertSourceMode(EditorMode.FLUIDS, PreviewIngredientKind.FLUID);
		assertSourceMode(EditorMode.OTHER_TYPES, PreviewIngredientKind.GENERIC);
	}

	@Test
	void nonSourceModesKeepRightPreviewWithoutSourceGridControls() {
		for (EditorMode mode : List.of(EditorMode.RULES, EditorMode.APPEARANCE)) {
			assertFalse(mode.sourceContentMode());
			assertFalse(mode.sourceIngredientKind().isPresent());
			assertFalse(mode.searchEnabled());
			assertFalse(mode.hideUsedEnabled());
			assertFalse(mode.ownershipLabelsEnabled());
			assertFalse(mode.sourceGridEnabled());
			assertTrue(mode.rightPreviewEnabled());
		}
	}

	@Test
	void sourceCellStatesExposePressedMutedOwnershipWithoutAllowingToggle() {
		IngredientSourceCellState muted = IngredientSourceCellState.muted(List.of("Other Group"));

		assertEquals(IngredientSourceCellVisualState.PRESSED_MUTED, muted.visualState());
		assertTrue(muted.pressed());
		assertTrue(muted.muted());
		assertTrue(muted.hasOwnerTooltip());
		assertEquals(List.of("Other Group"), muted.ownerGroupNames());
		assertFalse(muted.activeInCurrentGroup());
		assertFalse(muted.canToggleCurrentGroup());
	}

	@Test
	void activeStateWinsOverMutedOwnershipWhenResolving() {
		IngredientSourceCellState state = IngredientSourceCellState.resolve(true, List.of("Other Group"), false);

		assertEquals(IngredientSourceCellVisualState.PRESSED_ACTIVE, state.visualState());
		assertTrue(state.pressed());
		assertFalse(state.muted());
		assertTrue(state.activeInCurrentGroup());
		assertTrue(state.canToggleCurrentGroup());
		assertEquals(List.of("Other Group"), state.ownerGroupNames());
	}

	@Test
	void normalAndDisabledStatesHaveExpectedToggleBehavior() {
		IngredientSourceCellState normal = IngredientSourceCellState.normal();
		IngredientSourceCellState disabled = IngredientSourceCellState.disabled();

		assertEquals(IngredientSourceCellVisualState.NORMAL, normal.visualState());
		assertFalse(normal.pressed());
		assertTrue(normal.canToggleCurrentGroup());

		assertEquals(IngredientSourceCellVisualState.DISABLED, disabled.visualState());
		assertFalse(disabled.pressed());
		assertFalse(disabled.canToggleCurrentGroup());
	}

	private static void assertSourceMode(EditorMode mode, PreviewIngredientKind kind) {
		assertTrue(mode.sourceContentMode());
		assertEquals(EditorModeCategory.SOURCE_CONTENT, mode.category());
		assertEquals(kind, mode.sourceIngredientKind().orElseThrow());
		assertTrue(mode.searchEnabled());
		assertTrue(mode.hideUsedEnabled());
		assertTrue(mode.ownershipLabelsEnabled());
		assertTrue(mode.sourceGridEnabled());
		assertTrue(mode.rightPreviewEnabled());
	}
}
