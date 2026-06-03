package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Static helpers for rendering a single 16?16 fluid or generic ingredient cell
 * inside the GroupEditor panels, extracted to avoid duplicating render logic across
 * {@link EditorLeftPanel} and {@link EditorRightPanel}.
 */
final class IngredientCellRenderer {

	private IngredientCellRenderer() {}

	static void renderFluid(GuiGraphics g, EditorFluidIngredientView fluid, int x, int y) {
		EditorFluidIngredientHelper.render(g, fluid, x, y);
	}

	static void renderGeneric(GuiGraphics g, GenericIngredientView entry, int x, int y) {
		EditorGenericIngredientHelper.render(g, entry, x, y);
	}
}
