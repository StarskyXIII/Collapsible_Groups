package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter;
import com.starskyxiii.collapsible_groups.compat.jei.manager.GroupsButtonController;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBorderRenderer;
import com.starskyxiii.collapsible_groups.viewer.ViewerOverlayHook;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.input.GuiTextFieldFilter;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Shared render-phase, layout, and input behavior behind the loader overlay mixins. */
public final class JeiIngredientListOverlayController {
	private static final int BUTTON_GAP = 2;

	private final IconButton configButton;
	private final GuiTextFieldFilter searchField;
	private final Supplier<ImmutableRect2i> searchArea;
	private final BooleanSupplier listDisplayed;
	private final BooleanSupplier configuredVisible;
	private final IconButton groupsButton = new IconButton(new GroupsButtonController());

	public JeiIngredientListOverlayController(IconButton configButton, GuiTextFieldFilter searchField,
		Supplier<ImmutableRect2i> searchArea, BooleanSupplier listDisplayed, BooleanSupplier configuredVisible) {
		this.configButton = configButton;
		this.searchField = searchField;
		this.searchArea = searchArea;
		this.listDisplayed = listDisplayed;
		this.configuredVisible = configuredVisible;
	}

	public void drawBackgroundPhase(GuiGraphics graphics) {
		GroupBorderRenderer.clear();
		if (!listDisplayed.getAsBoolean()) return;
		syncBoundsToConfigButton(shouldShowGroupsButton());
	}

	public void drawForegroundPhase(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		GroupBorderRenderer.renderAndClear(graphics);
		if (shouldShowGroupsButton()) groupsButton.draw(graphics, mouseX, mouseY, partialTicks);
	}

	public void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
		if (shouldShowGroupsButton()) groupsButton.drawTooltips(graphics, mouseX, mouseY);
	}

	public IUserInputHandler wrapInputHandler(IUserInputHandler original) {
		IUserInputHandler groupsHandler = groupsButton.createInputHandler();
		IUserInputHandler combined = new CombinedInputHandler("IngredientListOverlay_withGroups", groupsHandler, original);
		return new ProxyInputHandler(() -> shouldShowGroupsButton() ? combined : original);
	}

	private void syncBoundsToConfigButton(boolean showGroupsButton) {
		ImmutableRect2i configArea = configButton.getArea();
		if (configArea == null || configArea.isEmpty()) return;
		ImmutableRect2i currentSearchArea = searchArea.get();
		if (currentSearchArea == null || currentSearchArea.isEmpty()) return;

		ViewerOverlayHook.Bounds buttonBounds = new ViewerOverlayHook.Bounds(
			configArea.getX(), configArea.getY(), configArea.getWidth(), configArea.getHeight());
		if (showGroupsButton) {
			buttonBounds = JeiViewerAdapter.instance().overlayHook().placeButton(buttonBounds, BUTTON_GAP);
			ImmutableRect2i groupsArea = new ImmutableRect2i(
				buttonBounds.x(), buttonBounds.y(), buttonBounds.width(), buttonBounds.height());
			if (!groupsArea.equals(groupsButton.getArea())) groupsButton.updateBounds(groupsArea);
		}
		int adjustedWidth = JeiViewerAdapter.instance().overlayHook().adjustSearchFieldWidth(
			new ViewerOverlayHook.Bounds(currentSearchArea.getX(), currentSearchArea.getY(),
				currentSearchArea.getWidth(), currentSearchArea.getHeight()), buttonBounds, BUTTON_GAP);
		if (currentSearchArea.getWidth() != adjustedWidth) {
			searchField.updateBounds(new ImmutableRect2i(currentSearchArea.getX(), currentSearchArea.getY(),
				adjustedWidth, currentSearchArea.getHeight()));
		}
	}

	private boolean shouldShowGroupsButton() {
		return JeiViewerAdapter.instance().overlayHook().shouldShowButton(
			configuredVisible.getAsBoolean(), listDisplayed.getAsBoolean());
	}
}
