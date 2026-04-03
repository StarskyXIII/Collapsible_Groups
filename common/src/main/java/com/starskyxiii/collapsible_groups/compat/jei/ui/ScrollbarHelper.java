package com.starskyxiii.collapsible_groups.compat.jei.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Static utility for scrollbar rendering and drag/click math used by the group editor screens. */
public final class ScrollbarHelper {

	public static final int WIDTH = 4;
	public static final int GAP   = 2;

	private ScrollbarHelper() {}

	/** Renders a vertical scrollbar track + thumb. */
	public static void render(GuiGraphicsExtractor guiGraphics, int x, int y, int height, int visibleRows, int totalRows, int scrollRow) {
		// Match the group manager scrollbar so the editor does not show a bright left highlight.
		guiGraphics.fill(x, y, x + WIDTH, y + height, 0x18667799);
		if (totalRows <= visibleRows || totalRows <= 0) {
			guiGraphics.fill(x, y, x + WIDTH, y + height, 0x22334455);
			return;
		}
		int thumbH  = Math.max(14, height * visibleRows / totalRows);
		int travel  = height - thumbH;
		int thumbY  = y + travel * scrollRow / Math.max(1, totalRows - visibleRows);
		guiGraphics.fill(x, thumbY, x + WIDTH, thumbY + thumbH, 0x6699AABB);
	}

	/**
	 * Translates a mouse click on the scrollbar track to a new scroll row.
	 * Returns {@code currentRow} unchanged if the click lands on the thumb.
	 */
	public static int trackClickToRow(double mouseY, int trackY, int height, int totalRows, int visibleRows, int currentRow) {
		int maxScroll = Math.max(0, totalRows - visibleRows);
		if (maxScroll == 0) return 0;
		int thumbH = Math.max(12, height * visibleRows / Math.max(1, totalRows));
		int travel = height - thumbH;
		int thumbY = trackY + (travel > 0 ? travel * currentRow / maxScroll : 0);
		if (mouseY >= thumbY && mouseY < thumbY + thumbH) return currentRow;
		return clamp((int) ((mouseY - trackY - thumbH / 2.0) * maxScroll / Math.max(1, travel)), 0, maxScroll);
	}

	/** Computes a new scroll row during a thumb-drag gesture. */
	public static int dragToRow(double mouseY, double startMouseY, int startRow, int totalRows, int visibleRows, int height) {
		int maxScroll = Math.max(0, totalRows - visibleRows);
		if (maxScroll == 0) return 0;
		int thumbH = Math.max(12, height * visibleRows / Math.max(1, totalRows));
		int travel  = height - thumbH;
		if (travel <= 0) return startRow;
		double delta = mouseY - startMouseY;
		return clamp((int) Math.round(startRow + delta * maxScroll / travel), 0, maxScroll);
	}

	/** Renders a vertical scrollbar for pixel-based scrolling surfaces. */
	public static void renderPixels(GuiGraphicsExtractor guiGraphics, int x, int y, int height,
	                                int visibleHeight, int contentHeight, int scrollOffset) {
		guiGraphics.fill(x, y, x + WIDTH, y + height, 0x18667799);
		if (contentHeight <= visibleHeight || contentHeight <= 0) {
			guiGraphics.fill(x, y, x + WIDTH, y + height, 0x22334455);
			return;
		}
		int thumbH = Math.max(14, height * visibleHeight / contentHeight);
		int travel = height - thumbH;
		int thumbY = y + travel * scrollOffset / Math.max(1, contentHeight - visibleHeight);
		guiGraphics.fill(x, thumbY, x + WIDTH, thumbY + thumbH, 0x6699AABB);
	}

	/**
	 * Translates a mouse click on a pixel-based scrollbar track to a new scroll offset.
	 * Returns {@code currentOffset} unchanged if the click lands on the thumb.
	 */
	public static int trackClickToOffset(double mouseY, int trackY, int height,
	                                     int contentHeight, int visibleHeight, int currentOffset) {
		int maxScroll = Math.max(0, contentHeight - visibleHeight);
		if (maxScroll == 0) return 0;
		int thumbH = Math.max(12, height * visibleHeight / Math.max(1, contentHeight));
		int travel = height - thumbH;
		int thumbY = trackY + (travel > 0 ? travel * currentOffset / maxScroll : 0);
		if (mouseY >= thumbY && mouseY < thumbY + thumbH) return currentOffset;
		return clamp((int) ((mouseY - trackY - thumbH / 2.0) * maxScroll / Math.max(1, travel)), 0, maxScroll);
	}

	/** Computes a new scroll offset during a pixel-based thumb-drag gesture. */
	public static int dragToOffset(double mouseY, double startMouseY, int startOffset,
	                               int contentHeight, int visibleHeight, int height) {
		int maxScroll = Math.max(0, contentHeight - visibleHeight);
		if (maxScroll == 0) return 0;
		int thumbH = Math.max(12, height * visibleHeight / Math.max(1, contentHeight));
		int travel = height - thumbH;
		if (travel <= 0) return startOffset;
		double delta = mouseY - startMouseY;
		return clamp((int) Math.round(startOffset + delta * maxScroll / travel), 0, maxScroll);
	}

	public static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
