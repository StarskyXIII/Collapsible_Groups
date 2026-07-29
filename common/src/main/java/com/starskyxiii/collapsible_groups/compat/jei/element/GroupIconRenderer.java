package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientRenderBridge;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Renders the stacked-icon visual for collapsible group headers.
 *
 * <p>Rendering approach based on REI's CollapsedEntriesBorderRenderer:
 * <ul>
 *   <li>All ingredients are scaled to 90% and centered within the 16x16 slot</li>
 *   <li>Up to 2 ingredients are offset diagonally to create a stacked appearance</li>
 *   <li>Z-depth separation (+10 per layer) prevents z-fighting</li>
 *   <li>A +/- indicator in the bottom-right corner shows the expand/collapse state</li>
 * </ul>
 *
 * <p>Item rendering uses vanilla {@code GuiGraphicsExtractor.item()}.
 * Non-item ingredients (fluids, generics) fall back to JEI's own renderer via {@link JeiRuntimeHolder}.
 */
public final class GroupIconRenderer implements IIngredientRenderer<GroupIcon> {
	static final float INGREDIENT_SCALE = 0.9f;

	@Override
	public void render(GuiGraphicsExtractor g, GroupIcon icon) {
		render(g, icon, 0, 0);
	}

	@Override
	public void render(GuiGraphicsExtractor g, GroupIcon icon, int posX, int posY) {
		List<ITypedIngredient<?>> items = icon.displayIngredients();
		if (items.isEmpty()) return;

		List<Layer> layers = layers(posX, posY, items.size());
		for (int i = 0; i < layers.size(); i++) {
			if (i > 0) {
				g.nextStratum();
			}
			Layer layer = layers.get(i);
			renderIngredient(g, items.get(layer.ingredientIndex()), layer);
		}

		// --- Expand/collapse indicator ---
		g.nextStratum();
		g.text(Minecraft.getInstance().font,
			icon.isExpanded() ? "-" : "+", posX + 10, posY + 9, 0xFFFFFFFF, true);
	}

	@Override
	public List<Component> getTooltip(GroupIcon ingredient, TooltipFlag tooltipFlag) {
		// Tooltip is handled by GroupHeaderElement.getTooltip(), not here.
		return List.of();
	}

	/**
	 * Renders a single ingredient at the given absolute pixel position.
	 * Items use vanilla rendering; non-items delegate to JEI's renderer.
	 */
	@SuppressWarnings("unchecked")
	private static void renderIngredient(GuiGraphicsExtractor g, ITypedIngredient<?> typed, Layer layer) {
		g.pose().pushMatrix();
		try {
			// Scale around the layer's absolute top-left. JEI still receives absolute
			// coordinates, so renderer-owned scissors contain the scaled sprite.
			g.pose().translate(layer.x(), layer.y());
			g.pose().scale(layer.scale(), layer.scale());
			g.pose().translate(-layer.x(), -layer.y());

			var itemOpt = typed.getItemStack();
			if (itemOpt.isPresent()) {
				g.item(itemOpt.get(), layer.x(), layer.y());
				return;
			}

			var runtime = JeiRuntimeHolder.get();
			if (runtime != null) {
				renderViaJei(g, (ITypedIngredient<Object>) typed, runtime, layer.x(), layer.y());
			}
		} finally {
			g.pose().popMatrix();
		}
	}

	private static void renderViaJei(
		GuiGraphicsExtractor g, ITypedIngredient<Object> typed,
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
