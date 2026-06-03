package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class IngredientCellRenderer {

	private IngredientCellRenderer() {}

	static void renderFluid(GuiGraphicsExtractor g, EditorFluidIngredientView fluid, int x, int y) {
		EditorFluidIngredientHelper.render(g, fluid, x, y);
	}

	static void renderGeneric(GuiGraphicsExtractor g, GenericIngredientView entry, int x, int y) {
		EditorGenericIngredientHelper.render(g, entry, x, y);
	}
}
