package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;
import com.starskyxiii.collapsible_groups.compat.jei.ui.OreEditorShellLayout;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreEditorShellContractTest {
	@Test
	void topLevelShellModesKeepResultPreviewVisible() {
		for (OreEditorShellMode mode : OreEditorShellMode.values()) {
			assertTrue(mode.rightPreviewEnabled());
		}
		assertTrue(OreEditorShellMode.CONTENTS.contentControlsEnabled());
		assertFalse(OreEditorShellMode.RULES.contentControlsEnabled());
		assertTrue(OreEditorShellMode.RULES.rulesPanelEnabled());
		assertTrue(OreEditorShellMode.LOOK.appearancePanelEnabled());
	}

	@Test
	void contentFiltersMapToSingleEditorSourceKinds() {
		assertEquals(PreviewIngredientKind.ITEM, OreEditorContentFilter.ITEMS.ingredientKind());
		assertEquals(PreviewIngredientKind.FLUID, OreEditorContentFilter.FLUIDS.ingredientKind());
		assertEquals(PreviewIngredientKind.GENERIC, OreEditorContentFilter.OTHER_TYPES.ingredientKind());
		for (OreEditorContentFilter filter : OreEditorContentFilter.values()) {
			assertTrue(filter.searchEnabled());
			assertTrue(filter.hideUsedEnabled());
			assertTrue(filter.ownershipLabelsEnabled());
		}
	}

	@Test
	void shellLayoutKeepsEditorAndPreviewNonOverlapping() {
		OreEditorShellLayout layout = OreEditorShellLayout.compute(360, 240);

		assertEquals(OreEditorShellLayout.OUTER_MARGIN, layout.editorPanel().x());
		assertTrue(layout.editorPanel().right() < layout.previewPanel().x());
		assertTrue(layout.previewPanel().right() <= 360 - OreEditorShellLayout.OUTER_MARGIN);
		assertTrue(layout.previewPanel().width() >= 112);
		assertTrue(layout.panelLayout().leftCols() >= 1);
		assertTrue(layout.panelLayout().rightCols() >= 1);
		assertTrue(layout.panelLayout().leftRows() >= 1);
		assertTrue(layout.panelLayout().rightRows() >= 1);
	}

	@Test
	void shellLayoutConstrainsHeaderAndPreviewProportions() {
		assertEquals(75, OreEditorShellLayout.HEADER_HEIGHT);
		assertEquals(5, OreEditorShellLayout.FIRST_ROW_Y);
		assertEquals(28, OreEditorShellLayout.SECOND_ROW_Y);
		assertEquals(51, OreEditorShellLayout.THIRD_ROW_Y);
		for (int width : new int[] {320, 360, 480, 854, 1024, 1280, 1920}) {
			OreEditorShellLayout layout = OreEditorShellLayout.compute(width, 480);

			assertEquals(OreEditorShellLayout.OUTER_MARGIN, layout.actionTitle().x());
			assertEquals(layout.saveButton().x() - OreEditorShellLayout.OUTER_MARGIN - OreEditorShellLayout.GAP,
				layout.actionTitle().width());
			assertTrue(layout.actionTitle().right() <= layout.saveButton().x() - OreEditorShellLayout.GAP);
			assertEquals(width - OreEditorShellLayout.OUTER_MARGIN, layout.cancelButton().right());
			assertTrue(layout.saveButton().right() <= layout.cancelButton().x() - OreEditorShellLayout.GAP);
			assertNull(layout.disableSourceCheckbox());
			assertNull(layout.disableSourceLabel());
			assertEquals(OreEditorShellLayout.PREVIEW_HEADER_HEIGHT, layout.headerPreview().width());
			assertEquals(OreEditorShellLayout.OUTER_MARGIN, layout.headerPreview().x());
			assertTrue(layout.headerPreview().right() < layout.nameField().x());
			assertEquals(layout.headerPreview().right() + OreEditorShellLayout.HEADER_PREVIEW_NAME_GAP,
				layout.nameField().x());
			assertTrue(layout.nameField().width() <= OreEditorShellLayout.HEADER_NAME_MAX_WIDTH);
			assertTrue(layout.nameField().right() <= width - OreEditorShellLayout.OUTER_MARGIN);
			assertTrue(layout.actionTitle().y() < layout.nameField().y());
			assertTrue(layout.nameField().y() < layout.modeSegmentRow().y());
			assertTrue(layout.nameField().bottom() <= OreEditorShellLayout.HEADER_HEIGHT);
			assertTrue(layout.saveButton().bottom() <= OreEditorShellLayout.HEADER_HEIGHT);
			assertTrue(layout.cancelButton().bottom() <= OreEditorShellLayout.HEADER_HEIGHT);
			assertTrue(layout.modeSegmentRow().bottom() <= OreEditorShellLayout.HEADER_HEIGHT);
			assertTrue(layout.modeSegmentRow().right() <= width - OreEditorShellLayout.OUTER_MARGIN);
			assertTrue(layout.footerStatus().right() < layout.footerHint().x());
			assertTrue(layout.editorPanel().y() >= OreEditorShellLayout.HEADER_HEIGHT + OreEditorShellLayout.GAP);
			assertTrue(layout.previewPanel().width() <= OreEditorShellLayout.PREVIEW_MAX_WIDTH);
			assertTrue(layout.previewPanel().width() >= OreEditorShellLayout.PREVIEW_MIN_WIDTH);
			if (width >= 480) {
				assertTrue(layout.editorPanel().width() > layout.previewPanel().width());
			}
		}
	}

	@Test
	void copyShellLayoutReservesDisableSourceOptionWithoutOverlappingNameField() {
		int measuredLabelWidth = 126;
		for (int width : new int[] {216, 320, 360, 480, 854, 1024, 1280, 1920}) {
			OreEditorShellLayout layout = OreEditorShellLayout.compute(width, 480, true, measuredLabelWidth);
			OreEditorShellLayout.Rect checkbox = layout.disableSourceCheckbox();
			OreEditorShellLayout.Rect label = layout.disableSourceLabel();
			assertNotNull(checkbox);
			assertNotNull(label);

			assertEquals(OreEditorShellLayout.DISABLE_SOURCE_CHECKBOX_SIZE, checkbox.width());
			assertEquals(OreEditorShellLayout.DISABLE_SOURCE_CHECKBOX_SIZE, checkbox.height());
			assertTrue(checkbox.y() >= OreEditorShellLayout.THIRD_ROW_Y);
			assertTrue(checkbox.bottom() <= OreEditorShellLayout.THIRD_ROW_Y + OreEditorShellLayout.SEGMENT_HEIGHT);
			assertEquals(OreEditorShellLayout.THIRD_ROW_Y, label.y());
			assertEquals(OreEditorShellLayout.SEGMENT_HEIGHT, label.height());
			assertTrue(checkbox.x() >= layout.modeSegmentRow().right() + OreEditorShellLayout.GAP);
			assertEquals(width - OreEditorShellLayout.OUTER_MARGIN, label.right());
			if (width >= 480) {
				assertEquals(measuredLabelWidth, label.width());
			} else {
				assertTrue(label.width() <= measuredLabelWidth);
			}
			assertTrue(layout.headerPreview().right() < layout.nameField().x());
			assertTrue(layout.nameField().width() <= OreEditorShellLayout.HEADER_NAME_MAX_WIDTH);
			assertTrue(checkbox.right() < label.x());
			assertEquals(OreEditorShellLayout.SECOND_ROW_Y, layout.nameField().y());
			assertTrue(layout.nameField().right() <= width - OreEditorShellLayout.OUTER_MARGIN);
		}
	}

	@Test
	void copyShellLayoutClipsLongDisableSourceLabelWhileKeepingNameFieldUsable() {
		int measuredLabelWidth = 1000;
		OreEditorShellLayout layout = OreEditorShellLayout.compute(320, 480, true, measuredLabelWidth);
		OreEditorShellLayout.Rect checkbox = layout.disableSourceCheckbox();
		OreEditorShellLayout.Rect label = layout.disableSourceLabel();
		assertNotNull(checkbox);
		assertNotNull(label);

		assertEquals(320 - OreEditorShellLayout.OUTER_MARGIN, label.right());
		assertTrue(label.width() < measuredLabelWidth);
		assertTrue(checkbox.x() >= layout.modeSegmentRow().right() + OreEditorShellLayout.GAP);
		assertTrue(checkbox.y() >= OreEditorShellLayout.THIRD_ROW_Y);
		assertTrue(checkbox.bottom() <= OreEditorShellLayout.THIRD_ROW_Y + OreEditorShellLayout.SEGMENT_HEIGHT);
		assertEquals(OreEditorShellLayout.THIRD_ROW_Y, label.y());
		assertEquals(OreEditorShellLayout.SEGMENT_HEIGHT, label.height());
	}

	@Test
	void copyShellLayoutAvoidsModeSegmentOverlapOnNarrowScreens() {
		OreEditorShellLayout layout = OreEditorShellLayout.compute(216, 240, true, 192);
		OreEditorShellLayout.Rect checkbox = layout.disableSourceCheckbox();
		OreEditorShellLayout.Rect label = layout.disableSourceLabel();
		assertNotNull(checkbox);
		assertNotNull(label);

		assertTrue(checkbox.x() >= layout.modeSegmentRow().right() + OreEditorShellLayout.GAP);
		assertEquals(216 - OreEditorShellLayout.OUTER_MARGIN, label.right());
		assertTrue(label.width() < 192);
	}

	@Test
	void shellLayoutKeepsContentsToolbarAboveGrid() {
		for (int width : new int[] {360, 480, 854, 1280, 1920}) {
			OreEditorShellLayout layout = OreEditorShellLayout.compute(width, 480);

			assertEquals(18, OreEditorShellLayout.SEGMENT_HEIGHT);
			assertEquals(OreEditorShellLayout.SEGMENT_HEIGHT, layout.contentTypeRow().height());
			assertEquals(OreEditorShellLayout.SEGMENT_HEIGHT, layout.searchField().height());
			assertEquals(OreEditorShellLayout.SEGMENT_HEIGHT, layout.hideUsedButton().height());
			assertEquals(17, EditorLayout.ITEM_SIZE);
			assertEquals(layout.editorPanel().y() + OreEditorShellLayout.PANEL_INSET, layout.contentTypeRow().y());
			assertTrue(layout.contentTypeRow().bottom() < layout.searchField().y());
			assertTrue(layout.searchField().bottom() < layout.contentCount().y());
			assertTrue(layout.contentCount().bottom() < layout.panelLayout().gridTop());
			assertTrue(layout.searchField().right() < layout.hideUsedButton().x());
			assertTrue(layout.panelLayout().gridTop() < layout.editorPanel().bottom());
		}
	}

	@Test
	void compactContentsToolbarSurvivesShortScreens() {
		for (int width : new int[] {360, 480, 854}) {
			OreEditorShellLayout layout = OreEditorShellLayout.compute(width, 240);

			assertTrue(layout.searchField().right() < layout.hideUsedButton().x());
			assertTrue(layout.contentCount().bottom() < layout.panelLayout().gridTop());
			assertTrue(layout.panelLayout().gridTop() < layout.editorPanel().bottom());
			assertTrue(layout.previewLayout().gridTop() < layout.panelLayout().gridTop());
		}
	}

	@Test
	void shellLayoutGivesPreviewItsOwnHigherGrid() {
		OreEditorShellLayout layout = OreEditorShellLayout.compute(854, 480);

		assertTrue(layout.previewTitle().bottom() < layout.previewBody().y());
		assertTrue(layout.previewTitle().bottom() < layout.previewHeader().y());
		assertEquals(OreEditorShellLayout.PREVIEW_HEADER_HEIGHT, layout.previewHeader().height());
		assertTrue(layout.previewHeader().bottom() < layout.previewBody().y());
		assertEquals(layout.previewBody().y(), layout.previewLayout().gridTop());
		assertTrue(layout.previewLayout().gridTop() < layout.panelLayout().gridTop());
		assertTrue(layout.previewLayout().gridTop() < layout.previewPanel().bottom());
	}

	@Test
	void previewSummaryUsesShortSingleKindLabelsAndTotalMixedLabels() {
		OreEditorPreviewSummary itemsOnly = OreEditorPreviewSummary.of(185, 0, 0);
		OreEditorPreviewSummary fluidsOnly = OreEditorPreviewSummary.of(0, 7, 0);
		OreEditorPreviewSummary genericOnly = OreEditorPreviewSummary.of(0, 0, 4);
		OreEditorPreviewSummary mixed = OreEditorPreviewSummary.of(185, 2, 3);

		assertEquals(ModTranslationKeys.ORE_EDITOR_PREVIEW_SUMMARY_ITEMS_ONLY, itemsOnly.labelKey());
		assertArrayEquals(new Object[] {185}, itemsOnly.labelArgs());
		assertEquals(ModTranslationKeys.ORE_EDITOR_PREVIEW_SUMMARY_FLUIDS_ONLY, fluidsOnly.labelKey());
		assertArrayEquals(new Object[] {7}, fluidsOnly.labelArgs());
		assertEquals(ModTranslationKeys.ORE_EDITOR_PREVIEW_SUMMARY_GENERIC_ONLY, genericOnly.labelKey());
		assertArrayEquals(new Object[] {4}, genericOnly.labelArgs());
		assertEquals(ModTranslationKeys.COUNT_ENTRIES, mixed.labelKey());
		assertArrayEquals(new Object[] {190}, mixed.labelArgs());
	}
}
