package com.starskyxiii.collapsible_groups.client.editor.model;

import com.starskyxiii.collapsible_groups.client.editor.model.EditorContentFilter;
import com.starskyxiii.collapsible_groups.client.editor.model.EditorShellMode;
import com.starskyxiii.collapsible_groups.client.preview.model.EditorPreviewSummary;
import com.starskyxiii.collapsible_groups.client.preview.model.PreviewIngredientKind;

import com.starskyxiii.collapsible_groups.client.widget.EditorLayout;
import com.starskyxiii.collapsible_groups.client.widget.EditorShellLayout;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorShellContractTest {
	@Test
	void topLevelShellModesKeepResultPreviewVisible() {
		for (EditorShellMode mode : EditorShellMode.values()) {
			assertTrue(mode.rightPreviewEnabled());
		}
		assertTrue(EditorShellMode.CONTENTS.contentControlsEnabled());
		assertFalse(EditorShellMode.RULES.contentControlsEnabled());
		assertTrue(EditorShellMode.RULES.rulesPanelEnabled());
		assertTrue(EditorShellMode.LOOK.appearancePanelEnabled());
	}

	@Test
	void contentFiltersMapToSingleEditorSourceKinds() {
		assertEquals(PreviewIngredientKind.ITEM, EditorContentFilter.ITEMS.ingredientKind());
		assertEquals(PreviewIngredientKind.FLUID, EditorContentFilter.FLUIDS.ingredientKind());
		assertEquals(PreviewIngredientKind.GENERIC, EditorContentFilter.OTHER_TYPES.ingredientKind());
		for (EditorContentFilter filter : EditorContentFilter.values()) {
			assertTrue(filter.searchEnabled());
			assertTrue(filter.hideUsedEnabled());
			assertTrue(filter.ownershipLabelsEnabled());
		}
	}

	@Test
	void shellLayoutKeepsEditorAndPreviewNonOverlapping() {
		EditorShellLayout layout = EditorShellLayout.compute(360, 240);

		assertEquals(EditorShellLayout.OUTER_MARGIN, layout.editorPanel().x());
		assertTrue(layout.editorPanel().right() < layout.previewPanel().x());
		assertTrue(layout.previewPanel().right() <= 360 - EditorShellLayout.OUTER_MARGIN);
		assertTrue(layout.previewPanel().width() >= 112);
		assertTrue(layout.panelLayout().leftCols() >= 1);
		assertTrue(layout.panelLayout().rightCols() >= 1);
		assertTrue(layout.panelLayout().leftRows() >= 1);
		assertTrue(layout.panelLayout().rightRows() >= 1);
	}

	@Test
	void shellLayoutConstrainsHeaderAndPreviewProportions() {
		assertEquals(75, EditorShellLayout.HEADER_HEIGHT);
		assertEquals(5, EditorShellLayout.FIRST_ROW_Y);
		assertEquals(28, EditorShellLayout.SECOND_ROW_Y);
		assertEquals(51, EditorShellLayout.THIRD_ROW_Y);
		for (int width : new int[] {320, 360, 480, 854, 1024, 1280, 1920}) {
			EditorShellLayout layout = EditorShellLayout.compute(width, 480);

			assertEquals(EditorShellLayout.OUTER_MARGIN, layout.actionTitle().x());
			assertEquals(layout.saveButton().x() - EditorShellLayout.OUTER_MARGIN - EditorShellLayout.GAP,
				layout.actionTitle().width());
			assertTrue(layout.actionTitle().right() <= layout.saveButton().x() - EditorShellLayout.GAP);
			assertEquals(width - EditorShellLayout.OUTER_MARGIN, layout.cancelButton().right());
			assertTrue(layout.saveButton().right() <= layout.cancelButton().x() - EditorShellLayout.GAP);
			assertNull(layout.disableSourceCheckbox());
			assertNull(layout.disableSourceLabel());
			assertEquals(EditorShellLayout.PREVIEW_HEADER_HEIGHT, layout.headerPreview().width());
			assertEquals(EditorShellLayout.OUTER_MARGIN, layout.headerPreview().x());
			assertTrue(layout.headerPreview().right() < layout.nameField().x());
			assertEquals(layout.headerPreview().right() + EditorShellLayout.HEADER_PREVIEW_NAME_GAP,
				layout.nameField().x());
			assertTrue(layout.nameField().width() <= EditorShellLayout.HEADER_NAME_MAX_WIDTH);
			assertTrue(layout.nameField().right() <= width - EditorShellLayout.OUTER_MARGIN);
			assertTrue(layout.actionTitle().y() < layout.nameField().y());
			assertTrue(layout.nameField().y() < layout.modeSegmentRow().y());
			assertTrue(layout.nameField().bottom() <= EditorShellLayout.HEADER_HEIGHT);
			assertTrue(layout.saveButton().bottom() <= EditorShellLayout.HEADER_HEIGHT);
			assertTrue(layout.cancelButton().bottom() <= EditorShellLayout.HEADER_HEIGHT);
			assertTrue(layout.modeSegmentRow().bottom() <= EditorShellLayout.HEADER_HEIGHT);
			assertTrue(layout.modeSegmentRow().right() <= width - EditorShellLayout.OUTER_MARGIN);
			assertTrue(layout.footerStatus().right() < layout.footerHint().x());
			assertTrue(layout.editorPanel().y() >= EditorShellLayout.HEADER_HEIGHT + EditorShellLayout.GAP);
			assertTrue(layout.previewPanel().width() <= EditorShellLayout.PREVIEW_MAX_WIDTH);
			assertTrue(layout.previewPanel().width() >= EditorShellLayout.PREVIEW_MIN_WIDTH);
			if (width >= 480) {
				assertTrue(layout.editorPanel().width() > layout.previewPanel().width());
			}
		}
	}

	@Test
	void copyShellLayoutReservesDisableSourceOptionWithoutOverlappingNameField() {
		int measuredLabelWidth = 126;
		for (int width : new int[] {216, 320, 360, 480, 854, 1024, 1280, 1920}) {
			EditorShellLayout layout = EditorShellLayout.compute(width, 480, true, measuredLabelWidth);
			EditorShellLayout.Rect checkbox = layout.disableSourceCheckbox();
			EditorShellLayout.Rect label = layout.disableSourceLabel();
			assertNotNull(checkbox);
			assertNotNull(label);

			assertEquals(EditorShellLayout.DISABLE_SOURCE_CHECKBOX_SIZE, checkbox.width());
			assertEquals(EditorShellLayout.DISABLE_SOURCE_CHECKBOX_SIZE, checkbox.height());
			assertTrue(checkbox.y() >= EditorShellLayout.THIRD_ROW_Y);
			assertTrue(checkbox.bottom() <= EditorShellLayout.THIRD_ROW_Y + EditorShellLayout.SEGMENT_HEIGHT);
			assertEquals(EditorShellLayout.THIRD_ROW_Y, label.y());
			assertEquals(EditorShellLayout.SEGMENT_HEIGHT, label.height());
			assertTrue(checkbox.x() >= layout.modeSegmentRow().right() + EditorShellLayout.GAP);
			assertEquals(width - EditorShellLayout.OUTER_MARGIN, label.right());
			if (width >= 480) {
				assertEquals(measuredLabelWidth, label.width());
			} else {
				assertTrue(label.width() <= measuredLabelWidth);
			}
			assertTrue(layout.headerPreview().right() < layout.nameField().x());
			assertTrue(layout.nameField().width() <= EditorShellLayout.HEADER_NAME_MAX_WIDTH);
			assertTrue(checkbox.right() < label.x());
			assertEquals(EditorShellLayout.SECOND_ROW_Y, layout.nameField().y());
			assertTrue(layout.nameField().right() <= width - EditorShellLayout.OUTER_MARGIN);
		}
	}

	@Test
	void copyShellLayoutClipsLongDisableSourceLabelWhileKeepingNameFieldUsable() {
		int measuredLabelWidth = 1000;
		EditorShellLayout layout = EditorShellLayout.compute(320, 480, true, measuredLabelWidth);
		EditorShellLayout.Rect checkbox = layout.disableSourceCheckbox();
		EditorShellLayout.Rect label = layout.disableSourceLabel();
		assertNotNull(checkbox);
		assertNotNull(label);

		assertEquals(320 - EditorShellLayout.OUTER_MARGIN, label.right());
		assertTrue(label.width() < measuredLabelWidth);
		assertTrue(checkbox.x() >= layout.modeSegmentRow().right() + EditorShellLayout.GAP);
		assertTrue(checkbox.y() >= EditorShellLayout.THIRD_ROW_Y);
		assertTrue(checkbox.bottom() <= EditorShellLayout.THIRD_ROW_Y + EditorShellLayout.SEGMENT_HEIGHT);
		assertEquals(EditorShellLayout.THIRD_ROW_Y, label.y());
		assertEquals(EditorShellLayout.SEGMENT_HEIGHT, label.height());
	}

	@Test
	void copyShellLayoutAvoidsModeSegmentOverlapOnNarrowScreens() {
		EditorShellLayout layout = EditorShellLayout.compute(216, 240, true, 192);
		EditorShellLayout.Rect checkbox = layout.disableSourceCheckbox();
		EditorShellLayout.Rect label = layout.disableSourceLabel();
		assertNotNull(checkbox);
		assertNotNull(label);

		assertTrue(checkbox.x() >= layout.modeSegmentRow().right() + EditorShellLayout.GAP);
		assertEquals(216 - EditorShellLayout.OUTER_MARGIN, label.right());
		assertTrue(label.width() < 192);
	}

	@Test
	void shellLayoutKeepsContentsToolbarAboveGrid() {
		for (int width : new int[] {360, 480, 854, 1280, 1920}) {
			EditorShellLayout layout = EditorShellLayout.compute(width, 480);

			assertEquals(18, EditorShellLayout.SEGMENT_HEIGHT);
			assertEquals(EditorShellLayout.SEGMENT_HEIGHT, layout.contentTypeRow().height());
			assertEquals(EditorShellLayout.SEGMENT_HEIGHT, layout.searchField().height());
			assertEquals(EditorShellLayout.SEGMENT_HEIGHT, layout.hideUsedButton().height());
			assertEquals(17, EditorLayout.ITEM_SIZE);
			assertEquals(layout.editorPanel().y() + EditorShellLayout.PANEL_INSET, layout.contentTypeRow().y());
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
			EditorShellLayout layout = EditorShellLayout.compute(width, 240);

			assertTrue(layout.searchField().right() < layout.hideUsedButton().x());
			assertTrue(layout.contentCount().bottom() < layout.panelLayout().gridTop());
			assertTrue(layout.panelLayout().gridTop() < layout.editorPanel().bottom());
			assertTrue(layout.previewLayout().gridTop() < layout.panelLayout().gridTop());
		}
	}

	@Test
	void shellLayoutGivesPreviewItsOwnHigherGrid() {
		EditorShellLayout layout = EditorShellLayout.compute(854, 480);

		assertTrue(layout.previewTitle().bottom() < layout.previewBody().y());
		assertTrue(layout.previewTitle().bottom() < layout.previewHeader().y());
		assertEquals(EditorShellLayout.PREVIEW_HEADER_HEIGHT, layout.previewHeader().height());
		assertTrue(layout.previewHeader().bottom() < layout.previewBody().y());
		assertEquals(layout.previewBody().y(), layout.previewLayout().gridTop());
		assertTrue(layout.previewLayout().gridTop() < layout.panelLayout().gridTop());
		assertTrue(layout.previewLayout().gridTop() < layout.previewPanel().bottom());
	}

	@Test
	void previewSummaryUsesShortSingleKindLabelsAndTotalMixedLabels() {
		EditorPreviewSummary itemsOnly = EditorPreviewSummary.of(185, 0, 0);
		EditorPreviewSummary fluidsOnly = EditorPreviewSummary.of(0, 7, 0);
		EditorPreviewSummary genericOnly = EditorPreviewSummary.of(0, 0, 4);
		EditorPreviewSummary mixed = EditorPreviewSummary.of(185, 2, 3);

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
