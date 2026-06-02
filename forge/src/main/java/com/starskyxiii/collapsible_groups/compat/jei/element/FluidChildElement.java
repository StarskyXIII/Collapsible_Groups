package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBorderRenderer;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupThemeResolver;
import com.starskyxiii.collapsible_groups.platform.Services;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.IngredientGridTooltipHelper;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class FluidChildElement implements IElement<FluidStack> {
	private final IngredientElement<FluidStack> delegate;
	private final String groupId;

	public FluidChildElement(ITypedIngredient<FluidStack> ingredient, String groupId) {
		this.delegate = new IngredientElement<>(ingredient);
		this.groupId = groupId;
	}

	@Override public ITypedIngredient<FluidStack> getTypedIngredient() { return delegate.getTypedIngredient(); }
	@Override public Optional<IBookmark> getBookmark()                { return delegate.getBookmark(); }

	@Override
	public @Nullable IDrawable createRenderOverlay() {
		return new FluidChildOverlay(groupId);
	}

	@Override public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) { delegate.show(recipesGui, focusUtil, roles); }
	@Override public void getTooltip(JeiTooltip tooltip, IngredientGridTooltipHelper helper, IIngredientRenderer<FluidStack> renderer, IIngredientHelper<FluidStack> ingredientHelper) { delegate.getTooltip(tooltip, helper, renderer, ingredientHelper); }
	@Override public boolean isVisible()                              { return delegate.isVisible(); }
	@Override public boolean handleClick(UserInput input, IInternalKeyMappings keyBindings) { return delegate.handleClick(input, keyBindings); }

	private static class FluidChildOverlay implements IDrawable {
		private final String groupId;
		FluidChildOverlay(String groupId) { this.groupId = groupId; }
		@Override public int getWidth()  { return 16; }
		@Override public int getHeight() { return 16; }
		@Override public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
			if (Services.CONFIG.showGroupBackgrounds()) {
				guiGraphics.fill(xOffset - 1, yOffset - 1, xOffset + 17, yOffset + 17,
					GroupThemeResolver.expandedGroupBackgroundColor(groupId));
			}
			GroupBorderRenderer.registerPosition(groupId, xOffset, yOffset);
		}
	}
}
