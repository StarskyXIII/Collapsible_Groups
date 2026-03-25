package com.starskyxiii.collapsible_groups.compat.jei.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Small helper for drawing single-line editor text with width clipping and
 * exposing a matching hover region for full-text tooltips.
 */
public record HoverableTextLine(
	String fullText,
	String displayText,
	int x,
	int y,
	int width,
	int color
) {
	private static final String ELLIPSIS = "...";

	public static HoverableTextLine create(Font font, String text, int x, int y, int maxWidth, int color) {
		if (font == null || text == null || text.isEmpty() || maxWidth <= 0) {
			return new HoverableTextLine(text == null ? "" : text, "", x, y, 0, color);
		}
		String displayText = clipWithEllipsis(font, text, maxWidth);
		return new HoverableTextLine(text, displayText, x, y, Math.min(maxWidth, font.width(displayText)), color);
	}

	public void render(GuiGraphics g, Font font) {
		if (width <= 0 || displayText.isEmpty()) {
			return;
		}
		g.drawString(font, displayText, x, y, color, false);
	}

	public boolean isHovered(double mouseX, double mouseY, Font font) {
		return width > 0
			&& mouseX >= x
			&& mouseX < x + width
			&& mouseY >= y
			&& mouseY < y + font.lineHeight;
	}

	public List<Component> tooltip(Font font, int maxWidth) {
		return List.of(Component.literal(fullText));
	}

	private static String clipWithEllipsis(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}

		int ellipsisWidth = font.width(ELLIPSIS);
		if (ellipsisWidth >= maxWidth) {
			return font.plainSubstrByWidth(ELLIPSIS, maxWidth);
		}

		String clipped = font.plainSubstrByWidth(text, maxWidth - ellipsisWidth);
		while (!clipped.isEmpty() && font.width(clipped + ELLIPSIS) > maxWidth) {
			clipped = clipped.substring(0, clipped.length() - 1);
		}
		return clipped + ELLIPSIS;
	}
}
