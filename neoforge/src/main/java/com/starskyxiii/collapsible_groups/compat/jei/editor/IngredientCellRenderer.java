package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Static helpers for rendering a single 16?16 fluid or generic ingredient cell
 * inside the GroupEditor panels, extracted to avoid duplicating render logic across
 * {@link EditorLeftPanel} and {@link EditorRightPanel}.
 */
final class IngredientCellRenderer {

	private IngredientCellRenderer() {}

	static void renderFluid(GuiGraphicsExtractor g, FluidStack fluid, int x, int y) {
		var runtime = JeiRuntimeHolder.get();
		if (runtime != null) {
			var renderer = runtime.getIngredientManager().getIngredientRenderer(NeoForgeTypes.FLUID_STACK);
			g.enableScissor(x, y, x + 16, y + 16);
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			renderer.render(g, fluid);
			g.pose().popMatrix();
			g.disableScissor();
		} else {
			var bucketItem = fluid.getFluid().getBucket();
			if (bucketItem != Items.AIR) g.item(new ItemStack(bucketItem), x, y);
		}
	}

	static void renderGeneric(GuiGraphicsExtractor g, GenericIngredientView entry, int x, int y) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		entry.renderer().render(g, entry.ingredient());
		g.pose().popMatrix();
	}
}
