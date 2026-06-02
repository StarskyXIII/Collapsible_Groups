package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import mezz.jei.api.forge.ForgeTypes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;

final class IngredientCellRenderer {

	private IngredientCellRenderer() {}

	static void renderFluid(GuiGraphics g, FluidStack fluid, int x, int y) {
		var runtime = JeiRuntimeHolder.get();
		if (runtime != null) {
			var renderer = runtime.getIngredientManager().getIngredientRenderer(ForgeTypes.FLUID_STACK);
			g.enableScissor(x, y, x + 16, y + 16);
			g.pose().pushPose();
			g.pose().translate(x, y, 0);
			renderer.render(g, fluid);
			g.pose().popPose();
			g.disableScissor();
			return;
		}

		var bucketItem = fluid.getFluid().getBucket();
		if (bucketItem != Items.AIR) {
			g.renderItem(new ItemStack(bucketItem), x, y);
		}
	}
}
