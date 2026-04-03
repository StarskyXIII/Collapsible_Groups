package com.starskyxiii.collapsible_groups.compat.jei.preview;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;

/**
 * Tooltip payload for rendering a mixed grid of item, fluid, and generic
 * ingredient previews.
 */
public record PreviewTooltipComponent(List<GroupPreviewEntry> entries) implements TooltipComponent, ClientTooltipComponent {
	public PreviewTooltipComponent {
		entries = List.copyOf(entries);
	}

	public ClientTooltipComponent createRenderer() {
		return this;
	}

	private static final int INGREDIENT_SIZE = 18;
	private static final int INGREDIENT_PADDING = 1;
	private static final int MAX_PER_LINE = 10;
	private static final int MAX_LINES = 3;
	private static final int MAX_INGREDIENTS = MAX_PER_LINE * MAX_LINES;

	@Override
	public int getHeight(Font font) {
		return getLineCount() * INGREDIENT_SIZE + (2 * INGREDIENT_PADDING);
	}

	@Override
	public int getWidth(Font font) {
		return getMaxPerLine() * INGREDIENT_SIZE + (2 * INGREDIENT_PADDING);
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor guiGraphics) {
		if (entries.size() <= MAX_INGREDIENTS) {
			drawEntries(guiGraphics, x, y, entries.size());
			return;
		}

		int drawCount = MAX_INGREDIENTS - 1;
		drawEntries(guiGraphics, x, y, drawCount);

		int remainingCount = Math.min(entries.size() - drawCount, 99);
		String countString = "+" + remainingCount;
		int textHeight = font.lineHeight - 1;
		int textWidth = font.width(countString);
		int textCenterX = x + (MAX_PER_LINE - 1) * INGREDIENT_SIZE + ((INGREDIENT_SIZE - textWidth) / 2);
		int textCenterY = y + (MAX_LINES - 1) * INGREDIENT_SIZE + ((INGREDIENT_SIZE - textHeight) / 2);
		guiGraphics.text(font, countString, textCenterX, textCenterY, 0xFFAAAAAA, false);
	}

	private void drawEntries(GuiGraphicsExtractor guiGraphics, int x, int y, int maxEntries) {
		int maxPerLine = divideCeil(maxEntries, getLineCount());
		for (int i = 0; i < entries.size() && i < maxEntries; i++) {
			int column = i % maxPerLine;
			int row = i / maxPerLine;
			entries.get(i).render(
				guiGraphics,
				x + column * INGREDIENT_SIZE + INGREDIENT_PADDING,
				y + row * INGREDIENT_SIZE + INGREDIENT_PADDING
			);
		}
	}

	private int getLineCount() {
		return Math.min(divideCeil(entries.size(), MAX_PER_LINE), MAX_LINES);
	}

	private int getMaxPerLine() {
		return Math.min(divideCeil(entries.size(), getLineCount()), MAX_PER_LINE);
	}

	private static int divideCeil(int value, int divisor) {
		return divisor <= 0 ? 0 : (value + divisor - 1) / divisor;
	}
}
