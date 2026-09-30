package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeServices;

import com.starskyxiii.collapsible_groups.client.editor.EditorFluidIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class IngredientCellRenderer {

	private IngredientCellRenderer() {}

	static void renderFluid(GuiGraphicsExtractor g, EditorFluidIngredientView fluid, int x, int y) {
		EditorRuntimeServices.presentation().renderFluid(g, fluid, x, y);
	}

	static void renderGeneric(GuiGraphicsExtractor g, EditorGenericIngredientView entry, int x, int y) {
		EditorRuntimeServices.presentation().renderGeneric(g, entry, x, y);
	}
}
