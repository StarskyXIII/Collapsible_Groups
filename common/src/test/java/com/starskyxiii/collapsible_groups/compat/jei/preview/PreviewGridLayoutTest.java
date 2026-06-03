package com.starskyxiii.collapsible_groups.compat.jei.preview;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewGridLayoutTest {
	@Test
	void computesEmptyGrid() {
		PreviewGridLayout layout = PreviewGridLayout.fixedColumns(0, 8, 3, 0);

		assertEquals(0, layout.totalRows());
		assertEquals(0, layout.drawCount());
		assertFalse(layout.hasOverflow());
	}

	@Test
	void exactPageDrawsEveryEntry() {
		PreviewGridLayout layout = PreviewGridLayout.fixedColumns(24, 8, 3, 0);

		assertEquals(3, layout.totalRows());
		assertEquals(24, layout.drawCount());
		assertEquals(0, layout.overflowCount());
		assertFalse(layout.hasOverflow());
	}

	@Test
	void overflowReservesLastCellForBadge() {
		PreviewGridLayout layout = PreviewGridLayout.fixedColumns(25, 8, 3, 0);

		assertEquals(4, layout.totalRows());
		assertEquals(23, layout.drawCount());
		assertEquals(2, layout.overflowCount());
		assertEquals(7, layout.overflowColumn());
		assertEquals(2, layout.overflowRow());
		assertTrue(layout.hasOverflow());
	}

	@Test
	void rowOffsetStartsAtScrolledRow() {
		PreviewGridLayout layout = PreviewGridLayout.fixedColumns(30, 8, 3, 1);
		List<Integer> indices = new ArrayList<>();

		layout.forEachCell((entryIndex, column, row) -> indices.add(entryIndex));

		assertEquals(8, layout.startIndex());
		assertEquals(22, layout.drawCount());
		assertEquals(List.of(
			8, 9, 10, 11, 12, 13, 14, 15,
			16, 17, 18, 19, 20, 21, 22, 23,
			24, 25, 26, 27, 28, 29
		), indices);
		assertFalse(layout.hasOverflow());
	}

	@Test
	void overflowCountIncludesReservedLastCellEntry() {
		PreviewGridLayout layout = PreviewGridLayout.fixedColumns(31, 8, 3, 0);

		assertEquals(23, layout.drawCount());
		assertEquals(8, layout.overflowCount());
		assertTrue(layout.hasOverflow());
	}
}
