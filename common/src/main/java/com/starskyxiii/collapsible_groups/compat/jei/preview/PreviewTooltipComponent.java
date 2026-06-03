package com.starskyxiii.collapsible_groups.compat.jei.preview;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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

	@Override
	public int getHeight() {
		return layout().height(INGREDIENT_SIZE, INGREDIENT_PADDING);
	}

	@Override
	public int getWidth(Font font) {
		return layout().width(INGREDIENT_SIZE, INGREDIENT_PADDING);
	}

	@Override
	public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
		PreviewTooltipLayout layout = layout();
		drawEntries(guiGraphics, x, y, layout);
		if (!layout.hasOverflow()) return;

		String countString = "+" + layout.overflowDisplayCount();
		int textHeight = font.lineHeight - 1;
		int textWidth = font.width(countString);
		int textCenterX = x + (MAX_PER_LINE - 1) * INGREDIENT_SIZE + ((INGREDIENT_SIZE - textWidth) / 2);
		int textCenterY = y + (MAX_LINES - 1) * INGREDIENT_SIZE + ((INGREDIENT_SIZE - textHeight) / 2);
		guiGraphics.drawString(font, countString, textCenterX, textCenterY, 0xAAAAAA, false);
	}

	private void drawEntries(GuiGraphics guiGraphics, int x, int y, PreviewTooltipLayout layout) {
		for (int i = 0; i < entries.size() && i < layout.drawCount(); i++) {
			entries.get(i).render(guiGraphics,
				x + layout.column(i) * INGREDIENT_SIZE + INGREDIENT_PADDING,
				y + layout.row(i) * INGREDIENT_SIZE + INGREDIENT_PADDING);
		}
	}

	private PreviewTooltipLayout layout() {
		return PreviewTooltipLayout.compute(entries.size(), MAX_PER_LINE, MAX_LINES);
	}
}
