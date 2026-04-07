package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.preview.PreviewTooltipComponent;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
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
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * Unified JEI element for all collapsible group header slots.
 *
 * <p>Uses {@link GroupIcon} as the ingredient type, giving the mod full
 * control over rendering via {@link GroupIconRenderer}. This eliminates
 * the need for separate Item/Fluid/Generic/Mixed header element subclasses.
 */
public final class GroupHeaderElement implements IElement<GroupIcon> {

	private final ITypedIngredient<GroupIcon> typedIcon;
	private final Component countLabel;
	private final List<GroupPreviewEntry> previewEntries;
	private final Runnable onToggle;

	public GroupHeaderElement(
		ITypedIngredient<GroupIcon> typedIcon,
		Component countLabel,
		List<GroupPreviewEntry> previewEntries,
		Runnable onToggle
	) {
		this.typedIcon = typedIcon;
		this.countLabel = countLabel;
		this.previewEntries = List.copyOf(previewEntries);
		this.onToggle = onToggle;
	}

	private GroupIcon icon() { return typedIcon.getIngredient(); }

	// --- IElement implementation ---

	@Override
	public ITypedIngredient<GroupIcon> getTypedIngredient() {
		return typedIcon;
	}

	@Override
	public Optional<IBookmark> getBookmark() {
		return Optional.empty();
	}

	@Override
	public IDrawable createRenderOverlay() {
		return new GroupExpandOverlay(icon().groupId());
	}

	@Override
	public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
		// Group placeholder - recipe lookup intentionally disabled.
	}

	@Override
	public boolean isVisible() {
		return true;
	}

	@Override
	public void getTooltip(JeiTooltip tooltip, IngredientGridTooltipHelper tooltipHelper,
	                       IIngredientRenderer<GroupIcon> renderer, IIngredientHelper<GroupIcon> helper) {
		tooltip.add(icon().displayNameComponent().copy().withStyle(ChatFormatting.GOLD));
		tooltip.add(countLabel);
		if (!icon().isExpanded()) {
			tooltip.add(new PreviewTooltipComponent(previewEntries));
		}
		String actionKey = icon().isExpanded() ? ModTranslationKeys.TOOLTIP_COLLAPSE : ModTranslationKeys.TOOLTIP_EXPAND;
		tooltip.add(Component.translatable(actionKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public boolean handleClick(UserInput input, IInternalKeyMappings keyBindings) {
		if (!input.is(keyBindings.getLeftClick())) return false;
		if (!input.isSimulate()) {
			GroupRegistry.toggleById(icon().groupId());
			onToggle.run();
		}
		return true;
	}
}
