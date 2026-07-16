package com.starskyxiii.collapsible_groups.client.editor.model;

import com.starskyxiii.collapsible_groups.client.editor.model.EditorMode;
import com.starskyxiii.collapsible_groups.client.editor.model.EditorModeCategory;
import com.starskyxiii.collapsible_groups.client.editor.model.IngredientSourceCellState;
import com.starskyxiii.collapsible_groups.client.editor.model.IngredientSourceCellVisualState;
import com.starskyxiii.collapsible_groups.client.preview.model.PreviewIngredientKind;

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
	void sourceCellStatesExposeOverlapOwnershipAndStayClickable() {
		// Overlap remains clickable so a click can add the ingredient to the current group.
		IngredientSourceCellState overlap = IngredientSourceCellState.overlap(List.of("Other Group"));

		assertEquals(IngredientSourceCellVisualState.OVERLAP, overlap.visualState());
		assertFalse(overlap.pressed());
		assertTrue(overlap.overlapped());
		assertTrue(overlap.hasOwnerTooltip());
		assertEquals(List.of("Other Group"), overlap.ownerGroupNames());
		assertFalse(overlap.activeInCurrentGroup());
		assertTrue(overlap.canToggleCurrentGroup());
	}

	@Test
	void activeStateWinsOverOverlapOwnershipWhenResolving() {
		IngredientSourceCellState state = IngredientSourceCellState.resolve(true, List.of("Other Group"), false);

		assertEquals(IngredientSourceCellVisualState.PRESSED_ACTIVE, state.visualState());
		assertTrue(state.pressed());
		assertFalse(state.overlapped());
		assertTrue(state.activeInCurrentGroup());
		assertTrue(state.canToggleCurrentGroup());
		assertEquals(List.of("Other Group"), state.ownerGroupNames());
	}

	@Test
	void resolveMapsSelectionAndOwnershipToThreeStates() {
		assertEquals(IngredientSourceCellVisualState.NORMAL,
			IngredientSourceCellState.resolve(false, List.of(), false).visualState());
		assertEquals(IngredientSourceCellVisualState.PRESSED_ACTIVE,
			IngredientSourceCellState.resolve(true, List.of(), false).visualState());
		assertEquals(IngredientSourceCellVisualState.OVERLAP,
			IngredientSourceCellState.resolve(false, List.of("Winner"), false).visualState());
	}

	@Test
	void ruleCoveredStateSharesGreenVisualButIsNotToggleable() {
		// a full match of the current group's rules that was not explicitly
		// picked — green like a selection, but not toggleable (the rule owns it).
		IngredientSourceCellState covered = IngredientSourceCellState.ruleCovered(List.of("Other Group"));

		assertEquals(IngredientSourceCellVisualState.RULE_COVERED, covered.visualState());
		assertTrue(covered.ruleCovered());
		assertTrue(covered.renderedAsSelected());
		assertFalse(covered.pressed());
		assertFalse(covered.overlapped());
		assertFalse(covered.activeInCurrentGroup());
		assertFalse(covered.canToggleCurrentGroup());
		// Other-group ownership is still carried so the overlap tooltip line survives.
		assertTrue(covered.hasOwnerTooltip());
		assertEquals(List.of("Other Group"), covered.ownerGroupNames());
	}

	@Test
	void resolvePrecedenceMatrixExplicitOverRuleCoveredOverOverlap() {
		List<String> owner = List.of("Winner");
		List<String> noOwner = List.of();

		// explicit always wins, regardless of ruleCovered / ownership
		for (boolean covered : new boolean[] {false, true}) {
			for (List<String> owners : List.of(noOwner, owner)) {
				assertEquals(IngredientSourceCellVisualState.PRESSED_ACTIVE,
					IngredientSourceCellState.resolve(true, covered, owners, false).visualState());
			}
		}

		// not explicit: ruleCovered beats overlap
		assertEquals(IngredientSourceCellVisualState.RULE_COVERED,
			IngredientSourceCellState.resolve(false, true, noOwner, false).visualState());
		assertEquals(IngredientSourceCellVisualState.RULE_COVERED,
			IngredientSourceCellState.resolve(false, true, owner, false).visualState());

		// not explicit, not ruleCovered: overlap when owned, else normal
		assertEquals(IngredientSourceCellVisualState.OVERLAP,
			IngredientSourceCellState.resolve(false, false, owner, false).visualState());
		assertEquals(IngredientSourceCellVisualState.NORMAL,
			IngredientSourceCellState.resolve(false, false, noOwner, false).visualState());

		// disabled short-circuits everything
		assertEquals(IngredientSourceCellVisualState.DISABLED,
			IngredientSourceCellState.resolve(true, true, owner, true).visualState());
	}

	@Test
	void ruleCoveredWithoutOtherOwnersCarriesNoOwnerTooltipSoHideUsedKeepsIt() {
		// hideUsed hides source cells whose *other-group* ownership is non-empty
		// (EditorItemSearchHelper.filterItems keys off that ownership map only, never
		// a rule-coverage set). A rule-covered cell that no other group owns therefore
		// has an empty ownership list and is not hidden — matching explicit selections.
		IngredientSourceCellState coveredOnly = IngredientSourceCellState.resolve(false, true, List.of(), false);
		assertEquals(IngredientSourceCellVisualState.RULE_COVERED, coveredOnly.visualState());
		assertFalse(coveredOnly.hasOwnerTooltip());
		assertTrue(coveredOnly.ownerGroupNames().isEmpty());
	}

	@Test
	void onlyRuleCoveredAndDisabledBlockToggling() {
		// The source grid's toggle gate keys off canToggleCurrentGroup(): explicit,
		// overlap and normal cells stay toggleable; rule-covered and disabled do not.
		assertTrue(IngredientSourceCellState.resolve(true, false, List.of(), false).canToggleCurrentGroup());
		assertTrue(IngredientSourceCellState.resolve(false, false, List.of("W"), false).canToggleCurrentGroup());
		assertTrue(IngredientSourceCellState.resolve(false, false, List.of(), false).canToggleCurrentGroup());
		assertFalse(IngredientSourceCellState.resolve(false, true, List.of(), false).canToggleCurrentGroup());
		assertFalse(IngredientSourceCellState.resolve(false, false, List.of(), true).canToggleCurrentGroup());
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
