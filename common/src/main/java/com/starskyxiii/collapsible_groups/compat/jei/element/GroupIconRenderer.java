package com.starskyxiii.collapsible_groups.compat.jei.element;

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

	@Override
	public void render(GuiGraphicsExtractor g, GroupIcon icon) {
		List<ITypedIngredient<?>> items = icon.displayIngredients();
		if (items.isEmpty()) return;

		// --- Stacked ingredient rendering (REI style) ---
		// Scale 0.9; positions derived from REI's FloatingRectangle(0.44/0.56, 0.56/0.44, 0.9, 0.9):
		//   center_back  = (0.56*16, 0.44*16) = (8.96, 7.04) -> top-left (2, 0) at scale 0.9
		//   center_front = (0.44*16, 0.56*16) = (7.04, 8.96) -> top-left (0, 2) at scale 0.9
		g.pose().pushMatrix();
		g.pose().scale(0.9f, 0.9f);

		if (items.size() == 1) {
			// Single item: center it: (16 - 16*0.9)/2 / 0.9 ~= 1
			renderIngredient(g, items.getFirst(), 1, 1);
		} else {
			// Back item (right-up)
			renderIngredient(g, items.get(1), 2, 0);
			g.nextStratum();
			// Front item (left-down)
			renderIngredient(g, items.getFirst(), 0, 2);
		}

		g.pose().popMatrix();

		// --- Expand/collapse indicator ---
		g.pose().pushMatrix();
		g.nextStratum();
		g.text(Minecraft.getInstance().font,
			icon.isExpanded() ? "-" : "+", 10, 9, 0xFFFFFFFF, true);
		g.pose().popMatrix();
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
	private static void renderIngredient(GuiGraphicsExtractor g, ITypedIngredient<?> typed, int x, int y) {
		var itemOpt = typed.getItemStack();
		if (itemOpt.isPresent()) {
			g.item(itemOpt.get(), x, y);
			return;
		}

		var runtime = JeiRuntimeHolder.get();
		if (runtime != null) {
			renderViaJei(g, (ITypedIngredient<Object>) typed, runtime, x, y);
		}
	}

	private static void renderViaJei(
		GuiGraphicsExtractor g, ITypedIngredient<Object> typed,
		mezz.jei.api.runtime.IJeiRuntime runtime, int x, int y
	) {
		var renderer = runtime.getIngredientManager().getIngredientRenderer(typed.getType());
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		renderer.render(g, typed.getIngredient());
		g.pose().popMatrix();
	}
}
