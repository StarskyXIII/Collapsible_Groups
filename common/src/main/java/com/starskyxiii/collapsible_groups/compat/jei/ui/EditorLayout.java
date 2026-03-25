package com.starskyxiii.collapsible_groups.compat.jei.ui;

/**
 * Immutable snapshot of the GroupEditorScreen layout computed each time the
 * screen is (re-)initialized. Both {@code EditorLeftPanel} and
 * {@code EditorRightPanel} receive this record instead of having to repeat the
 * same arithmetic.
 */
public record EditorLayout(
	int dividerX,
	int leftGridX,
	int rightGridX,
	int leftGridWidth,
	int rightGridWidth,
	int gridTop,
	int gridHeight,
	int leftCols,
	int leftRows,
	int rightCols,
	int rightRows,
	int leftScrollbarX,
	int rightScrollbarX
) {
	public static final int ITEM_SIZE          = 18;
	public static final int HEADER_HEIGHT      = 36;
	public static final int LABEL_ROW_HEIGHT   = 54;
	public static final int FOOTER_HEIGHT      = 28;
	public static final int DIVIDER_X_RATIO    = 55;
	public static final int GRID_TOP           = HEADER_HEIGHT + LABEL_ROW_HEIGHT + 4;
	public static final int GRID_BOTTOM_MARGIN = FOOTER_HEIGHT + 2;
	public static final int PANEL_INSET        = 3;

	public static EditorLayout compute(int screenWidth, int screenHeight) {
		int divX       = screenWidth * DIVIDER_X_RATIO / 100;
		int lgX        = 2;
		int rgX        = divX + 4;
		int lgW        = Math.max(ITEM_SIZE, divX - 4 - ScrollbarHelper.WIDTH - ScrollbarHelper.GAP);
		int rgW        = Math.max(ITEM_SIZE, screenWidth - divX - 6 - ScrollbarHelper.WIDTH - ScrollbarHelper.GAP);
		int gH         = Math.max(ITEM_SIZE, screenHeight - GRID_TOP - GRID_BOTTOM_MARGIN);
		int lCols      = Math.max(1, lgW / ITEM_SIZE);
		int lRows      = Math.max(1, gH  / ITEM_SIZE);
		int rCols      = Math.max(1, rgW / ITEM_SIZE);
		int rRows      = Math.max(1, gH  / ITEM_SIZE);
		int lSbX       = lgX + lgW + ScrollbarHelper.GAP;
		int rSbX       = rgX + rgW + ScrollbarHelper.GAP;
		return new EditorLayout(divX, lgX, rgX, lgW, rgW, GRID_TOP, gH, lCols, lRows, rCols, rRows, lSbX, rSbX);
	}

	public boolean isInsideLeft(double mouseX, double mouseY) {
		return mouseX >= leftGridX && mouseX < leftScrollbarX + ScrollbarHelper.WIDTH
			&& mouseY >= gridTop   && mouseY < gridTop + gridHeight;
	}

	public boolean isInsideRight(double mouseX, double mouseY) {
		return mouseX >= rightGridX && mouseX < rightScrollbarX + ScrollbarHelper.WIDTH
			&& mouseY >= gridTop    && mouseY < gridTop + gridHeight;
	}

	public static boolean isMouseOverCell(double mouseX, double mouseY, int cellX, int cellY) {
		return mouseX >= cellX && mouseX < cellX + 16 && mouseY >= cellY && mouseY < cellY + 16;
	}

	public static int totalRows(int count, int cols) {
		if (count <= 0) return 0;
		return Math.max(1, (count + cols - 1) / cols);
	}
}
