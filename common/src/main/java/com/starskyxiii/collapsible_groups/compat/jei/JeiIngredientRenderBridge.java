package com.starskyxiii.collapsible_groups.compat.jei;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Rendering boundary for JEI ingredients drawn by Collapsible Groups-owned UI.
 *
 * <p>JEI renderers may mix buffered {@link GuiGraphics} calls with immediate
 * draw calls, so pending UI geometry must be submitted before delegation.
 * Coordinates are passed through JEI's absolute-position overload because
 * renderer-owned clipping does not follow pose-stack translations.
 */
public final class JeiIngredientRenderBridge {
	private JeiIngredientRenderBridge() {}

	public static <T> void render(
		GuiGraphics graphics,
		IIngredientRenderer<T> renderer,
		T ingredient,
		int x,
		int y
	) {
		graphics.flush();
		renderer.render(graphics, ingredient, x, y);
	}
}
