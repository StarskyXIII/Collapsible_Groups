package com.starskyxiii.collapsible_groups.client.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorPanelSectionsTest {
	@Test
	void computesEmptySections() {
		EditorPanelSections sections = EditorPanelSections.compute(0, 0, 0, 4);

		assertEquals(0, sections.itemRows());
		assertEquals(0, sections.fluidRows());
		assertEquals(0, sections.genericRows());
		assertEquals(0, sections.totalRows());
		assertEquals(0, sections.fluidStartVRow());
		assertEquals(0, sections.genericStartVRow());
		assertFalse(sections.isItemRow(0));
		assertFalse(sections.isFluidRow(0));
		assertFalse(sections.isGenericRow(0));
	}

	@Test
	void computesItemOnlySections() {
		EditorPanelSections sections = EditorPanelSections.compute(5, 0, 0, 4);

		assertEquals(2, sections.itemRows());
		assertEquals(0, sections.fluidRows());
		assertEquals(0, sections.genericRows());
		assertEquals(2, sections.fluidStartVRow());
		assertEquals(2, sections.genericStartVRow());
		assertEquals(2, sections.totalRows());
		assertTrue(sections.isItemRow(0));
		assertTrue(sections.isItemRow(1));
		assertFalse(sections.isItemRow(2));
	}

	@Test
	void computesFluidOnlySections() {
		EditorPanelSections sections = EditorPanelSections.compute(0, 5, 0, 4);

		assertEquals(0, sections.itemRows());
		assertEquals(2, sections.fluidRows());
		assertEquals(0, sections.genericRows());
		assertEquals(0, sections.fluidStartVRow());
		assertEquals(2, sections.genericStartVRow());
		assertEquals(2, sections.totalRows());
		assertTrue(sections.isFluidRow(0));
		assertTrue(sections.isFluidRow(1));
		assertEquals(1, sections.fluidRow(1));
	}

	@Test
	void computesGenericOnlySections() {
		EditorPanelSections sections = EditorPanelSections.compute(0, 0, 5, 4);

		assertEquals(0, sections.itemRows());
		assertEquals(0, sections.fluidRows());
		assertEquals(2, sections.genericRows());
		assertEquals(0, sections.genericStartVRow());
		assertEquals(2, sections.totalRows());
		assertTrue(sections.isGenericRow(0));
		assertTrue(sections.isGenericRow(1));
		assertEquals(1, sections.genericRow(1));
	}

	@Test
	void computesItemAndGenericSections() {
		EditorPanelSections sections = EditorPanelSections.compute(5, 0, 5, 4);

		assertEquals(2, sections.itemRows());
		assertEquals(0, sections.fluidRows());
		assertEquals(2, sections.genericRows());
		assertEquals(2, sections.fluidStartVRow());
		assertEquals(2, sections.genericStartVRow());
		assertEquals(4, sections.totalRows());
		assertTrue(sections.isGenericRow(2));
		assertEquals(0, sections.genericRow(2));
	}

	@Test
	void computesFluidAndGenericSections() {
		EditorPanelSections sections = EditorPanelSections.compute(0, 5, 5, 4);

		assertEquals(0, sections.itemRows());
		assertEquals(2, sections.fluidRows());
		assertEquals(2, sections.genericRows());
		assertEquals(0, sections.fluidStartVRow());
		assertEquals(2, sections.genericStartVRow());
		assertEquals(4, sections.totalRows());
		assertTrue(sections.isGenericRow(2));
		assertEquals(0, sections.genericRow(2));
	}

	@Test
	void computesAllThreeSections() {
		EditorPanelSections sections = EditorPanelSections.compute(5, 5, 5, 4);

		assertEquals(2, sections.itemRows());
		assertEquals(2, sections.fluidRows());
		assertEquals(2, sections.genericRows());
		assertEquals(2, sections.fluidStartVRow());
		assertEquals(4, sections.genericStartVRow());
		assertEquals(6, sections.totalRows());
		assertTrue(sections.isFluidRow(2));
		assertTrue(sections.isGenericRow(4));
	}
}
