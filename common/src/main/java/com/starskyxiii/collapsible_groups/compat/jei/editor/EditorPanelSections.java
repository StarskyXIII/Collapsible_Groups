package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;

record EditorPanelSections(
	int itemRows,
	int fluidRows,
	int genericRows,
	int fluidStartVRow,
	int genericStartVRow,
	int totalRows
) {
	static EditorPanelSections compute(int itemCount, int fluidCount, int genericCount, int cols) {
		int safeCols = Math.max(1, cols);
		int itemRows = EditorLayout.totalRows(itemCount, safeCols);
		int fluidRows = EditorLayout.totalRows(fluidCount, safeCols);
		int genericRows = EditorLayout.totalRows(genericCount, safeCols);
		int fluidStartVRow = itemRows;
		int genericStartVRow = itemRows + fluidRows;
		int totalRows = itemRows + fluidRows + genericRows;
		return new EditorPanelSections(
			itemRows,
			fluidRows,
			genericRows,
			fluidStartVRow,
			genericStartVRow,
			totalRows);
	}

	boolean isItemRow(int vRow) {
		return vRow >= 0 && vRow < itemRows;
	}

	boolean isFluidRow(int vRow) {
		return fluidRows > 0 && vRow >= fluidStartVRow && vRow < fluidStartVRow + fluidRows;
	}

	int fluidRow(int vRow) {
		return vRow - fluidStartVRow;
	}

	boolean isGenericRow(int vRow) {
		return genericRows > 0 && vRow >= genericStartVRow && vRow < genericStartVRow + genericRows;
	}

	int genericRow(int vRow) {
		return vRow - genericStartVRow;
	}
}
