package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class IngredientCellRenderer {

	private IngredientCellRenderer() {}

	static void renderFluid(GuiGraphicsExtractor g, IJeiFluidIngredient fluid, int x, int y) {
		var runtime = JeiRuntimeHolder.get();
		if (runtime != null) {
			var renderer = runtime.getIngredientManager().getIngredientRenderer(FabricTypes.FLUID_STACK);
			g.enableScissor(x, y, x + 16, y + 16);
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			renderer.render(g, fluid);
			g.pose().popMatrix();
			g.disableScissor();
			return;
		}

		var bucketItem = fluid.getFluidVariant().getFluid().getBucket();
		if (bucketItem != Items.AIR) {
			g.item(new ItemStack(bucketItem), x, y);
		}
	}
}
