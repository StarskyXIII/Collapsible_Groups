package com.starskyxiii.collapsible_groups.compat.jei.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewTooltipLayoutTest {
	@Test
	void computesEmptyTooltip() {
		PreviewTooltipLayout layout = PreviewTooltipLayout.compute(0, 10, 3);

		assertEquals(0, layout.lineCount());
		assertEquals(0, layout.maxPerLine());
		assertEquals(0, layout.drawCount());
		assertEquals(2, layout.width(18, 1));
		assertEquals(2, layout.height(18, 1));
		assertFalse(layout.hasOverflow());
	}

	@Test
	void computesSingleRowTooltip() {
		PreviewTooltipLayout layout = PreviewTooltipLayout.compute(5, 10, 3);

		assertEquals(1, layout.lineCount());
		assertEquals(5, layout.maxPerLine());
		assertEquals(5, layout.drawCount());
		assertEquals(5, layout.drawColumns());
		assertEquals(2, layout.column(2));
		assertEquals(0, layout.row(2));
	}

	@Test
	void balancesMultipleRows() {
		PreviewTooltipLayout layout = PreviewTooltipLayout.compute(11, 10, 3);

		assertEquals(2, layout.lineCount());
		assertEquals(6, layout.maxPerLine());
		assertEquals(6, layout.drawColumns());
		assertEquals(5, layout.column(5));
		assertEquals(1, layout.row(6));
	}

	@Test
	void exactCapHasNoOverflow() {
		PreviewTooltipLayout layout = PreviewTooltipLayout.compute(30, 10, 3);

		assertEquals(3, layout.lineCount());
		assertEquals(10, layout.maxPerLine());
		assertEquals(30, layout.drawCount());
		assertEquals(0, layout.overflowDisplayCount());
		assertFalse(layout.hasOverflow());
	}

	@Test
	void overflowReservesLastSlot() {
		PreviewTooltipLayout layout = PreviewTooltipLayout.compute(31, 10, 3);

		assertEquals(3, layout.lineCount());
		assertEquals(10, layout.maxPerLine());
		assertEquals(29, layout.drawCount());
		assertEquals(10, layout.drawColumns());
		assertEquals(2, layout.overflowDisplayCount());
		assertTrue(layout.hasOverflow());
	}

	@Test
	void largeOverflowDisplayIsCapped() {
		PreviewTooltipLayout layout = PreviewTooltipLayout.compute(200, 10, 3);

		assertEquals(29, layout.drawCount());
		assertEquals(99, layout.overflowDisplayCount());
		assertTrue(layout.hasOverflow());
	}
}
