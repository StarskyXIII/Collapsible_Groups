package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBorderRenderer;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Wraps an individual generic ingredient belonging to a collapsible group.
 * Registers its render position with GroupBorderRenderer so generic groups
 * receive the same connected-border treatment as item and fluid groups.
 */
public class GenericChildElement<T> implements IElement<T>, PreRenderIngredientGridElement {
	private static final int EXPANDED_BACKGROUND = 0x14FFFFFF;

	private final IngredientElement<T> delegate;
	private final String groupId;

	public GenericChildElement(ITypedIngredient<T> ingredient, String groupId) {
		this.delegate = new IngredientElement<>(ingredient);
		this.groupId = groupId;
	}

	@Override
	public ITypedIngredient<T> getTypedIngredient() { return delegate.getTypedIngredient(); }

	@Override
	public Optional<IBookmark> getBookmark() { return delegate.getBookmark(); }

	@Override
	public @Nullable IDrawable createRenderOverlay() {
		return null;
	}

	@Override
	public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
		delegate.show(recipesGui, focusUtil, roles);
	}

	@Override
	public void getTooltip(JeiTooltip tooltip, IngredientGridTooltipHelper tooltipHelper,
	                       IIngredientRenderer<T> renderer, IIngredientHelper<T> helper) {
		delegate.getTooltip(tooltip, tooltipHelper, renderer, helper);
	}

	@Override
	public boolean isVisible() { return delegate.isVisible(); }

	@Override
	public boolean handleClick(UserInput input, IInternalKeyMappings keyBindings) {
		return delegate.handleClick(input, keyBindings);
	}

	@Override
	public void drawPreRender(GuiGraphicsExtractor guiGraphics, int xOffset, int yOffset) {
		guiGraphics.fill(xOffset - 1, yOffset - 1, xOffset + 17, yOffset + 17, EXPANDED_BACKGROUND);
		GroupBorderRenderer.registerPosition(groupId, xOffset, yOffset);
	}
}
