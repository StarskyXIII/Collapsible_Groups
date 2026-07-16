package com.starskyxiii.collapsible_groups.compat.jei.ui;

import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.group.GroupTheme;
import com.starskyxiii.collapsible_groups.group.GroupThemeColors;
import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders an editor-only JEI-grid preview of a collapsible group directly from
 * a {@link GroupTheme} (typically {@code AppearanceDraft.toTheme()}).
 *
 * <p>The helper owns the preview geometry and returns it to the screen so
 * rendering and hit-testing share one source of truth. Live JEI elements are
 * not touched: this class only reuses the same color resolution, stacked icon
 * visual, per-cell tint, and connected-border helper.
 */
public final class GroupSampleRenderer {
	/** Matches {@code GroupBorderRenderer.SPACING} / the live JEI slot pitch. */
	public static final int SLOT_PITCH = 18;
	public static final int ICON_SIZE = 16;
	private static final int PAGE_BUTTON_WIDTH = 20;
	private static final int PAGE_BUTTON_GAP = 3;
	private static final int PAGE_CONTROL_HEIGHT = UiSkinRenderer.BUTTON_DESIGN_HEIGHT;

	public record Fallbacks(int nameRgb, int collapsedHeaderArgb, int expandedHeaderArgb,
	                        int expandedGroupArgb, int expandedBorderArgb) {}

	public record Rect(int x, int y, int width, int height) {
		public int right() {
			return x + width;
		}

		public int bottom() {
			return y + height;
		}

		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
		}
	}

	public record Cell(Rect rect, int itemIndex, boolean header) {}

	public record Layout(
		Rect area,
		Rect headerCell,
		Rect previousPageButton,
		Rect nextPageButton,
		List<Cell> cells,
		int page,
		int pageCount,
		int childCapacity,
		int itemCount
	) {
		public boolean hasPages() {
			return pageCount > 1;
		}

		public boolean canPageBackward() {
			return page > 0;
		}

		public boolean canPageForward() {
			return page + 1 < pageCount;
		}
	}

	private GroupSampleRenderer() {}

	public static Layout layout(Rect area, boolean expanded, int itemCount, int requestedPage) {
		int controlsHeight = expanded ? PAGE_CONTROL_HEIGHT + PAGE_BUTTON_GAP : 0;
		int gridHeight = Math.max(SLOT_PITCH, area.height() - controlsHeight);
		int cols = Math.max(1, area.width() / SLOT_PITCH);
		int rows = Math.max(1, gridHeight / SLOT_PITCH);
		int totalCells = Math.max(1, cols * rows);
		int childCapacity = expanded ? Math.max(0, totalCells - 1) : 0;
		int pageCount = expanded && itemCount > 0 && childCapacity > 0
			? Math.max(1, (itemCount + childCapacity - 1) / childCapacity)
			: 1;
		int page = clamp(requestedPage, 0, pageCount - 1);

		List<Cell> cells = new ArrayList<>();
		Rect header = cellRect(area, cols, 0);
		cells.add(new Cell(header, -1, true));

		if (expanded && childCapacity > 0) {
			int start = page * childCapacity;
			int end = Math.min(itemCount, start + childCapacity);
			for (int itemIndex = start; itemIndex < end; itemIndex++) {
				int cellIndex = 1 + itemIndex - start;
				cells.add(new Cell(cellRect(area, cols, cellIndex), itemIndex, false));
			}
		}

		Rect previous = null;
		Rect next = null;
		if (pageCount > 1) {
			// Mirror the real JEI panel bottom row: ◀ pinned bottom-left, ▶ bottom-right,
			// with the "page/total" counter centered between them.
			int y = area.bottom() - PAGE_CONTROL_HEIGHT;
			previous = new Rect(area.x(), y, PAGE_BUTTON_WIDTH, PAGE_CONTROL_HEIGHT);
			next = new Rect(area.right() - PAGE_BUTTON_WIDTH, y, PAGE_BUTTON_WIDTH, PAGE_CONTROL_HEIGHT);
		}
		return new Layout(area, header, previous, next, List.copyOf(cells), page, pageCount, childCapacity, itemCount);
	}

	/**
	 * @return the full preview layout for hit-testing page buttons and header toggles.
	 */
	public static Layout render(GuiGraphicsExtractor g, Rect area, boolean expanded, int page, GroupTheme theme,
	                            List<GroupPreviewEntry> headerIcons, List<GroupPreviewEntry> items,
	                            Font font, Fallbacks fallbacks) {
		Layout layout = layout(area, expanded, items.size(), page);

		int expandedGroupBg = GroupThemeColors.expandedGroupBackground(theme, fallbacks.expandedGroupArgb());
		int border = GroupThemeColors.expandedGroupBorder(theme, fallbacks.expandedBorderArgb());
		int headerBg = expanded
			? GroupThemeColors.expandedHeaderBackground(theme, fallbacks.expandedHeaderArgb())
			: GroupThemeColors.collapsedHeaderBackground(theme, fallbacks.collapsedHeaderArgb());

		List<int[]> borderPositions = new ArrayList<>();
		for (Cell cell : layout.cells()) {
			Rect rect = cell.rect();
			if (cell.header()) {
				g.fill(rect.x() - 1, rect.y() - 1, rect.x() + 17, rect.y() + 17, headerBg);
				renderStackedIcons(g, headerIcons, rect.x(), rect.y(), expanded, font);
				if (expanded) {
					borderPositions.add(new int[]{rect.x(), rect.y()});
				}
			} else {
				g.fill(rect.x() - 1, rect.y() - 1, rect.x() + 17, rect.y() + 17, expandedGroupBg);
				items.get(cell.itemIndex()).render(g, rect.x(), rect.y());
				borderPositions.add(new int[]{rect.x(), rect.y()});
			}
		}

		if (expanded && !borderPositions.isEmpty()) {
			GroupBorderRenderer.drawBorder(g, borderPositions, border);
		}

		if (layout.hasPages()) {
			Rect prev = layout.previousPageButton();
			Rect nxt = layout.nextPageButton();
			UiSkinRenderer.drawButton(g, font, prev.x(), prev.y(), prev.width(), prev.height(), "<",
				buttonState(layout.canPageBackward()));
			UiSkinRenderer.drawButton(g, font, nxt.x(), nxt.y(), nxt.width(), nxt.height(), ">",
				buttonState(layout.canPageForward()));
			String counter = (layout.page() + 1) + "/" + layout.pageCount();
			int counterX = (prev.right() + nxt.x()) / 2 - font.width(counter) / 2;
			int counterY = prev.y() + Math.max(0, (prev.height() - font.lineHeight) / 2);
			g.text(font, counter, counterX, counterY, UiPalette.TEXT_MUTED, false);
		}
		return layout;
	}

	/**
	 * Draws up to two item icons stacked with a {@code +}/{@code -} indicator,
	 * mirroring {@code GroupIconRenderer} (scale 0.9, offsets (2,0)/(0,2)).
	 */
	public static void renderStackedIcons(GuiGraphicsExtractor g, List<GroupPreviewEntry> items, int x, int y,
	                                          boolean expanded, Font font) {
		if (!items.isEmpty()) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.nextStratum();
			g.pose().scale(0.9f, 0.9f);
			if (items.size() == 1) {
				items.getFirst().render(g, 1, 1);
			} else {
				items.get(1).render(g, 2, 0);
				g.nextStratum();
				items.getFirst().render(g, 0, 2);
			}
			g.pose().popMatrix();
		}
		g.pose().pushMatrix();
		g.nextStratum();
		g.text(font, expanded ? "-" : "+", x + 10, y + 9, 0xFFFFFFFF, true);
		g.pose().popMatrix();
	}

	private static Rect cellRect(Rect area, int cols, int cellIndex) {
		int col = cellIndex % cols;
		int row = cellIndex / cols;
		return new Rect(area.x() + col * SLOT_PITCH, area.y() + row * SLOT_PITCH, ICON_SIZE, ICON_SIZE);
	}

	private static UiSkinRenderer.ButtonState buttonState(boolean active) {
		return active ? UiSkinRenderer.ButtonState.NORMAL : UiSkinRenderer.ButtonState.DISABLED;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

}
