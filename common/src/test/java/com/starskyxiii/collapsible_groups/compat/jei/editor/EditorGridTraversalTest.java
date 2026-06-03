package com.starskyxiii.collapsible_groups.compat.jei.editor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorGridTraversalTest {
	@Test
	void visitsOnlyExistingCellsOnShortLastRow() {
		List<String> cells = new ArrayList<>();

		EditorGridTraversal.forRowCells(5, 1, 4, 10, 20,
			(index, x, y) -> cells.add(index + "@" + x + "," + y));

		assertEquals(List.of("4@10,20"), cells);
	}

	@Test
	void findsValidHitUsingCellHitbox() {
		assertEquals(4, EditorGridTraversal.findRowCellIndex(5, 1, 4, 10, 20, 10, 20));
		assertEquals(1, EditorGridTraversal.findRowCellIndex(8, 0, 4, 10, 20, 28, 20));
	}

	@Test
	void missesGutterBetweenCells() {
		assertEquals(-1, EditorGridTraversal.findRowCellIndex(8, 0, 4, 10, 20, 26, 20));
	}

	@Test
	void missesNonExistingCellOnShortLastRow() {
		assertEquals(-1, EditorGridTraversal.findRowCellIndex(5, 1, 4, 10, 20, 28, 20));
	}
}
