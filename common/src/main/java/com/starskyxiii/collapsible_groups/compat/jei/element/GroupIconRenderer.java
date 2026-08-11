package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientRenderBridge;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Renders the stacked-icon visual for collapsible group headers.
 *
 * <p>Rendering approach based on REI's CollapsedEntriesBorderRenderer:
 * <ul>
 *   <li>All ingredients are scaled to 90% and centred within the 16?16 slot</li>
 *   <li>Up to 2 ingredients are offset diagonally to create a stacked appearance</li>
 *   <li>Z-depth separation (+10 per layer) prevents z-fighting</li>
 *   <li>A +/- indicator in the bottom-right corner shows the expand/collapse state</li>
 * </ul>
 *
 * <p>Item rendering uses vanilla {@code GuiGraphics.renderItem()}.
 * Non-item ingredients (fluids, generics) fall back to JEI's own renderer via {@link JeiRuntimeHolder}.
 */
public final class GroupIconRenderer implements IIngredientRenderer<GroupIcon> {
	static final float INGREDIENT_SCALE = 0.9f;

	@Override
	public void render(GuiGraphics g, GroupIcon icon) {
		render(g, icon, 0, 0);
	}

	@Override
	public void render(GuiGraphics g, GroupIcon icon, int posX, int posY) {
		List<ITypedIngredient<?>> items = icon.displayIngredients();
		if (items.isEmpty()) return;

		for (Layer layer : layers(posX, posY, items.size())) {
			renderIngredient(g, items.get(layer.ingredientIndex()), layer);
		}

		// --- Expand/collapse indicator ---
		g.pose().pushPose();
		try {
			g.pose().translate(0, 0, 200);
			g.drawString(Minecraft.getInstance().font,
				icon.isExpanded() ? "-" : "+", posX + 10, posY + 9, 0xFFFFFFFF, true);
		} finally {
			g.pose().popPose();
		}
	}

	@Override
	public List<Component> getTooltip(GroupIcon ingredient, TooltipFlag tooltipFlag) {
		// Tooltip is handled by GroupHeaderElement.getTooltip(), not here.
		return List.of();
	}

	/**
	 * Renders a single ingredient at the given pixel offset.
	 * Items use vanilla rendering; non-items delegate to JEI's renderer.
	 */
	@SuppressWarnings("unchecked")
	private static void renderIngredient(GuiGraphics g, ITypedIngredient<?> typed, Layer layer) {
		g.pose().pushPose();
		try {
			// Scale around the layer's absolute top-left. JEI still receives absolute
			// coordinates, so renderer-owned scissors contain the scaled sprite.
			g.pose().translate(layer.x(), layer.y(), layer.z());
			g.pose().scale(layer.scale(), layer.scale(), layer.scale());
			g.pose().translate(-layer.x(), -layer.y(), 0);

			// Fast path: items use vanilla rendering (no JEI dependency)
			var itemOpt = typed.getItemStack();
			if (itemOpt.isPresent()) {
				g.renderItem(itemOpt.get(), layer.x(), layer.y());
				return;
			}

			// Fallback: non-items use JEI's registered renderer
			var runtime = JeiRuntimeHolder.get();
			if (runtime != null) {
				renderViaJei(g, (ITypedIngredient<Object>) typed, runtime, layer.x(), layer.y());
			}
		} finally {
			g.pose().popPose();
		}
	}

	private static void renderViaJei(
		GuiGraphics g, ITypedIngredient<Object> typed,
		mezz.jei.api.runtime.IJeiRuntime runtime, int x, int y
	) {
		var renderer = runtime.getIngredientManager().getIngredientRenderer(typed.getType());
		JeiIngredientRenderBridge.render(g, renderer, typed.getIngredient(), x, y);
	}

	static List<Layer> layers(int posX, int posY, int ingredientCount) {
		if (ingredientCount <= 0) return List.of();
		if (ingredientCount == 1) {
			return List.of(new Layer(0, posX + 1, posY + 1, 0, INGREDIENT_SCALE));
		}
		return List.of(
			new Layer(1, posX + 2, posY, 0, INGREDIENT_SCALE),
			new Layer(0, posX, posY + 2, 10, INGREDIENT_SCALE)
		);
	}

	record Layer(int ingredientIndex, int x, int y, int z, float scale) {}
}
