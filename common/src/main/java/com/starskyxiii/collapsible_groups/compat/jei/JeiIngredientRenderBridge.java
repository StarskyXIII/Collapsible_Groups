package com.starskyxiii.collapsible_groups.compat.jei;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class JeiIngredientRenderBridge {
	private JeiIngredientRenderBridge() {}

	public static <T> void render(
		GuiGraphicsExtractor graphics,
		IIngredientRenderer<T> renderer,
		T ingredient,
		int x,
		int y
	) {
		graphics.nextStratum();
		renderer.render(graphics, ingredient, x, y);
	}
}
