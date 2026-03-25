package com.starskyxiii.collapsible_groups.compat.jei.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Small shared drawing helpers for the tabbed group editor chrome.
 * This intentionally stays lightweight and avoids becoming a full UI toolkit.
 */
public final class EditorChrome {
	private static final int CENTER_TEXT_NUDGE = 1;
	private static final int TAB_ACTIVE_BG     = 0xAA27455B;
	private static final int TAB_INACTIVE_BG   = 0x66313A49;
	private static final int TAB_DISABLED_BG   = 0x44252A33;
	private static final int TAB_ACTIVE_BORDER = 0x66B8D7EA;
	private static final int TAB_BORDER        = 0x335E7C91;
	private static final int CHIP_ACTIVE_BG    = 0x88426F63;
	private static final int CHIP_BG           = 0x55313A49;
	private static final int CHIP_ACTIVE_EDGE  = 0x66A9D4B7;
	private static final int CHIP_EDGE         = 0x335E7C91;
	private static final int HOVER_OVERLAY     = 0x16FFFFFF;
	private static final int TAB_TEXT          = 0xDDECF6;
	private static final int TAB_MUTED_TEXT    = 0x8FA8B7;
	private static final int CHIP_TEXT         = 0xD7E8F1;
	private static final int CHIP_MUTED_TEXT   = 0x9BB0BD;

	private EditorChrome() {}

	public static int tabWidth(Font font, String label) {
		return Math.max(42, font.width(label) + 18);
	}

	public static int chipWidth(Font font, String label) {
		return Math.max(54, font.width(label) + 16);
	}

	public static void drawTab(GuiGraphics g, Font font, Rect rect, String label,
	                           boolean active, boolean enabled, boolean hovered) {
		int bg = enabled ? (active ? TAB_ACTIVE_BG : TAB_INACTIVE_BG) : TAB_DISABLED_BG;
		int border = active ? TAB_ACTIVE_BORDER : TAB_BORDER;
		g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bg);
		drawOutline(g, rect, border);
		if (hovered && enabled) {
			g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), HOVER_OVERLAY);
		}
		int color = enabled ? TAB_TEXT : TAB_MUTED_TEXT;
		int textX = rect.x() + Math.max(0, (rect.width() - font.width(label)) / 2);
		int textY = centeredTextY(font, rect.y(), rect.height());
		g.drawString(font, label, textX, textY, color, false);
	}

	public static void drawChip(GuiGraphics g, Font font, Rect rect, String label,
	                            boolean active, boolean hovered) {
		g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), active ? CHIP_ACTIVE_BG : CHIP_BG);
		drawOutline(g, rect, active ? CHIP_ACTIVE_EDGE : CHIP_EDGE);
		if (hovered) {
			g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), HOVER_OVERLAY);
		}
		int color = active ? CHIP_TEXT : CHIP_MUTED_TEXT;
		int textX = rect.x() + Math.max(0, (rect.width() - font.width(label)) / 2);
		int textY = centeredTextY(font, rect.y(), rect.height());
		g.drawString(font, label, textX, textY, color, false);
	}

	public static int centeredTextY(Font font, int top, int height) {
		return top + Math.max(0, (height - font.lineHeight) / 2) + CENTER_TEXT_NUDGE;
	}

	private static void drawOutline(GuiGraphics g, Rect rect, int color) {
		g.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, color);
		g.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), color);
		g.fill(rect.x(), rect.y() + 1, rect.x() + 1, rect.bottom() - 1, color);
		g.fill(rect.right() - 1, rect.y() + 1, rect.right(), rect.bottom() - 1, color);
	}

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
}
