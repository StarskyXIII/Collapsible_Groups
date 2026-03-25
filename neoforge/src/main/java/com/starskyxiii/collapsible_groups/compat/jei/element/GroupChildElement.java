package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBorderRenderer;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
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
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class GroupChildElement implements IElement<ItemStack> {
	private final IngredientElement<ItemStack> delegate;
	private final String groupId;

	public GroupChildElement(ITypedIngredient<ItemStack> ingredient, String groupId) {
		this.delegate = new IngredientElement<>(ingredient);
		this.groupId = groupId;
	}

	@Override
	public ITypedIngredient<ItemStack> getTypedIngredient() {
		return delegate.getTypedIngredient();
	}

	@Override
	public Optional<IBookmark> getBookmark() {
		return delegate.getBookmark();
	}

	@Override
	public @Nullable IDrawable createRenderOverlay() {
		return new GroupChildOverlay(groupId);
	}

	@Override
	public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
		delegate.show(recipesGui, focusUtil, roles);
	}

	@Override
	public void getTooltip(JeiTooltip tooltip, IngredientGridTooltipHelper tooltipHelper, IIngredientRenderer<ItemStack> ingredientRenderer, IIngredientHelper<ItemStack> ingredientHelper) {
		delegate.getTooltip(tooltip, tooltipHelper, ingredientRenderer, ingredientHelper);
	}

	@Override
	public boolean isVisible() {
		return delegate.isVisible();
	}

	@Override
	public boolean handleClick(UserInput input, IInternalKeyMappings keyBindings) {
		return delegate.handleClick(input, keyBindings);
	}

	private static class GroupChildOverlay implements IDrawable {
		private static final int EXPANDED_BACKGROUND = 0x34FFFFFF;
		private final String groupId;

		GroupChildOverlay(String groupId) {
			this.groupId = groupId;
		}

		@Override
		public int getWidth() { return 16; }

		@Override
		public int getHeight() { return 16; }

		@Override
		public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
			guiGraphics.fill(xOffset - 1, yOffset - 1, xOffset + 17, yOffset + 17, EXPANDED_BACKGROUND);
			GroupBorderRenderer.registerPosition(groupId, xOffset, yOffset);
		}
	}
}
