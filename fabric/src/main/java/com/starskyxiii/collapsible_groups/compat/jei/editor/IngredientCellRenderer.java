package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class IngredientCellRenderer {

	private IngredientCellRenderer() {}

	static void renderFluid(GuiGraphics g, IJeiFluidIngredient fluid, int x, int y) {
		var runtime = JeiRuntimeHolder.get();
		if (runtime != null) {
			var renderer = runtime.getIngredientManager().getIngredientRenderer(FabricTypes.FLUID_STACK);
			g.enableScissor(x, y, x + 16, y + 16);
			g.pose().pushPose();
			g.pose().translate(x, y, 0);
			renderer.render(g, fluid);
			g.pose().popPose();
			g.disableScissor();
			return;
		}

		var bucketItem = fluid.getFluidVariant().getFluid().getBucket();
		if (bucketItem != Items.AIR) {
			g.renderItem(new ItemStack(bucketItem), x, y);
		}
	}

	static void renderGeneric(GuiGraphics g, GenericIngredientView entry, int x, int y) {
		EditorGenericIngredientHelper.render(g, entry, x, y);
	}
}
