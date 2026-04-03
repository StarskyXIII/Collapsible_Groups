package com.starskyxiii.collapsible_groups.compat.jei.element;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * JEI ingredient-list hook for visuals that must be drawn before the
 * ingredient itself, such as translucent slot backgrounds.
 */
public interface PreRenderIngredientGridElement {
	void drawPreRender(GuiGraphicsExtractor guiGraphics, int xOffset, int yOffset);
}
