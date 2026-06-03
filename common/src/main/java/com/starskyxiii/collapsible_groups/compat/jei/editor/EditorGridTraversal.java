package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;

final class EditorGridTraversal {
	private EditorGridTraversal() {}

	@FunctionalInterface
	interface CellConsumer {
		void accept(int index, int x, int y);
	}

	static void forRowCells(int count, int row, int cols, int gridX, int y, CellConsumer consumer) {
		if (count <= 0 || row < 0 || cols <= 0) return;
		int rowStart = row * cols;
		for (int col = 0; col < cols && rowStart + col < count; col++) {
			int x = gridX + col * EditorLayout.ITEM_SIZE;
			consumer.accept(rowStart + col, x, y);
		}
	}

	static int findRowCellIndex(int count, int row, int cols, int gridX, int y, double mouseX, double mouseY) {
		if (count <= 0 || row < 0 || cols <= 0) return -1;
		int rowStart = row * cols;
		for (int col = 0; col < cols && rowStart + col < count; col++) {
			int index = rowStart + col;
			int x = gridX + col * EditorLayout.ITEM_SIZE;
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) return index;
		}
		return -1;
	}
}
