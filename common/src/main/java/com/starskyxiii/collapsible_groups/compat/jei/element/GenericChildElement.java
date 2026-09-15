package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBorderRenderer;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupThemeResolver;
import com.starskyxiii.collapsible_groups.platform.Services;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.overlay.elements.IngredientElement;
import net.minecraft.client.gui.GuiGraphics;

public class GenericChildElement<T> extends IngredientElement<T> implements PreRenderIngredientGridElement {

	private final String groupId;

	public GenericChildElement(ITypedIngredient<T> ingredient, String groupId) {
		super(ingredient);
		this.groupId = groupId;
	}

	@Override
	public void drawPreRender(GuiGraphics guiGraphics, int xOffset, int yOffset) {
		if (Services.CONFIG.showGroupBackgrounds()) {
			guiGraphics.fill(xOffset - 1, yOffset - 1, xOffset + 17, yOffset + 17,
				GroupThemeResolver.expandedGroupBackgroundColor(groupId));
		}
		GroupBorderRenderer.registerPosition(groupId, xOffset, yOffset);
	}
}
