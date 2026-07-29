package com.starskyxiii.collapsible_groups.compat.jei;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Rendering boundary for JEI ingredients drawn by Collapsible Groups-owned UI.
 *
 * <p>Coordinates are passed through JEI's absolute-position overload because
 * renderer-owned clipping does not follow pose-stack translations.
 */
public final class JeiIngredientRenderBridge {
	private JeiIngredientRenderBridge() {}

	public static <T> void render(
		GuiGraphicsExtractor graphics,
		IIngredientRenderer<T> renderer,
		T ingredient,
		int x,
		int y
	) {
		renderer.render(graphics, ingredient, x, y);
	}
}
