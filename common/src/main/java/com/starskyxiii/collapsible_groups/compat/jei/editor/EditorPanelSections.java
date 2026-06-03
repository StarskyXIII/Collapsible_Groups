package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;

record EditorPanelSections(
	int itemRows,
	int fluidRows,
	int genericRows,
	boolean hasItemSeparator,
	boolean hasFluidSeparator,
	int fluidStartVRow,
	int genericStartVRow,
	int totalRows
) {
	static EditorPanelSections compute(int itemCount, int fluidCount, int genericCount, int cols) {
		int safeCols = Math.max(1, cols);
		int itemRows = EditorLayout.totalRows(itemCount, safeCols);
		int fluidRows = EditorLayout.totalRows(fluidCount, safeCols);
		int genericRows = EditorLayout.totalRows(genericCount, safeCols);
		boolean hasItemSeparator = itemRows > 0 && (fluidRows > 0 || genericRows > 0);
		int fluidStartVRow = itemRows + (hasItemSeparator ? 1 : 0);
		boolean hasFluidSeparator = fluidRows > 0 && genericRows > 0;
		int genericStartVRow = fluidStartVRow + fluidRows + (hasFluidSeparator ? 1 : 0);
		int totalRows = itemRows + fluidRows + genericRows
			+ (hasItemSeparator ? 1 : 0)
			+ (hasFluidSeparator ? 1 : 0);
		return new EditorPanelSections(
			itemRows,
			fluidRows,
			genericRows,
			hasItemSeparator,
			hasFluidSeparator,
			fluidStartVRow,
			genericStartVRow,
			totalRows);
	}

	boolean isItemRow(int vRow) {
		return vRow >= 0 && vRow < itemRows;
	}

	boolean isItemSeparatorRow(int vRow) {
		return hasItemSeparator && vRow == itemRows;
	}

	boolean isFluidRow(int vRow) {
		return fluidRows > 0 && vRow >= fluidStartVRow && vRow < fluidStartVRow + fluidRows;
	}

	int fluidRow(int vRow) {
		return vRow - fluidStartVRow;
	}

	boolean isFluidSeparatorRow(int vRow) {
		return hasFluidSeparator && vRow == fluidStartVRow + fluidRows;
	}

	boolean isGenericRow(int vRow) {
		return genericRows > 0 && vRow >= genericStartVRow && vRow < genericStartVRow + genericRows;
	}

	int genericRow(int vRow) {
		return vRow - genericStartVRow;
	}
}
