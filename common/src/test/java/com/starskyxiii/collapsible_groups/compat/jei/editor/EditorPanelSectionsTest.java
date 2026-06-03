package com.starskyxiii.collapsible_groups.compat.jei.editor;

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
		assertFalse(sections.hasItemSeparator());
		assertFalse(sections.hasFluidSeparator());
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
		assertEquals(2, sections.totalRows());
		assertFalse(sections.hasItemSeparator());
		assertFalse(sections.hasFluidSeparator());
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
		assertEquals(2, sections.totalRows());
		assertFalse(sections.hasItemSeparator());
		assertFalse(sections.hasFluidSeparator());
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
		assertFalse(sections.hasItemSeparator());
		assertFalse(sections.hasFluidSeparator());
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
		assertTrue(sections.hasItemSeparator());
		assertFalse(sections.hasFluidSeparator());
		assertEquals(3, sections.fluidStartVRow());
		assertEquals(3, sections.genericStartVRow());
		assertEquals(5, sections.totalRows());
		assertTrue(sections.isItemSeparatorRow(2));
		assertTrue(sections.isGenericRow(3));
		assertEquals(0, sections.genericRow(3));
	}

	@Test
	void computesFluidAndGenericSections() {
		EditorPanelSections sections = EditorPanelSections.compute(0, 5, 5, 4);

		assertEquals(0, sections.itemRows());
		assertEquals(2, sections.fluidRows());
		assertEquals(2, sections.genericRows());
		assertFalse(sections.hasItemSeparator());
		assertTrue(sections.hasFluidSeparator());
		assertEquals(0, sections.fluidStartVRow());
		assertEquals(3, sections.genericStartVRow());
		assertEquals(5, sections.totalRows());
		assertTrue(sections.isFluidSeparatorRow(2));
		assertTrue(sections.isGenericRow(3));
		assertEquals(0, sections.genericRow(3));
	}

	@Test
	void computesAllThreeSections() {
		EditorPanelSections sections = EditorPanelSections.compute(5, 5, 5, 4);

		assertEquals(2, sections.itemRows());
		assertEquals(2, sections.fluidRows());
		assertEquals(2, sections.genericRows());
		assertTrue(sections.hasItemSeparator());
		assertTrue(sections.hasFluidSeparator());
		assertEquals(3, sections.fluidStartVRow());
		assertEquals(6, sections.genericStartVRow());
		assertEquals(8, sections.totalRows());
		assertTrue(sections.isItemSeparatorRow(2));
		assertTrue(sections.isFluidRow(3));
		assertTrue(sections.isFluidSeparatorRow(5));
		assertTrue(sections.isGenericRow(6));
	}
}
