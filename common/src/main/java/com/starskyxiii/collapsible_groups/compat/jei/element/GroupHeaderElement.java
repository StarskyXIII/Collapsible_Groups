package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.preview.PreviewTooltipComponent;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupThemeResolver;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
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
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Unified JEI element for all collapsible group header slots.
 * Uses {@link GroupIcon} as the ingredient type for full rendering control.
 */
public final class GroupHeaderElement implements IElement<GroupIcon>, PreRenderIngredientGridElement {

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

	@Override
	public ITypedIngredient<GroupIcon> getTypedIngredient() { return typedIcon; }

	@Override
	public Optional<IBookmark> getBookmark() { return Optional.empty(); }

	@Override
	public void tick() {}

	@Override
	public @Nullable IDrawable createRenderOverlay() { return null; }

	@Override
	public void drawPreRender(GuiGraphics guiGraphics, int xOffset, int yOffset) {
		if (!Services.CONFIG.showGroupBackgrounds()) return;

		boolean expanded = GroupRegistry.isExpandedById(icon().groupId());
		int background = GroupThemeResolver.headerBackgroundColor(icon().groupId(), expanded);
		guiGraphics.fill(xOffset - 1, yOffset - 1, xOffset + 17, yOffset + 17, background);
	}

	@Override
	public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {}

	@Override
	public boolean isVisible() { return true; }

	@Override
	public void getTooltip(JeiTooltip tooltip, IngredientGridTooltipHelper tooltipHelper,
	                       IIngredientRenderer<GroupIcon> renderer, IIngredientHelper<GroupIcon> helper) {
		tooltip.add(icon().displayNameComponent().copy()
			.withStyle(style -> style.withColor(TextColor.fromRgb(GroupThemeResolver.groupNameColor(icon().groupId())))));
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
