package com.starskyxiii.collapsible_groups.compat.jei.element;

import net.minecraft.client.gui.GuiGraphics;

/**
 * JEI ingredient-list hook for visuals that must be drawn before the
 * ingredient itself, such as translucent slot backgrounds.
 */
public interface PreRenderIngredientGridElement {
	void drawPreRender(GuiGraphics guiGraphics, int xOffset, int yOffset);
}
