package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.manager.GroupsButtonController;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBackgroundRenderer;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBorderRenderer;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.ViewerOverlayHook;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.input.GuiTextFieldFilter;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import mezz.jei.gui.overlay.IngredientListOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = IngredientListOverlay.class, remap = false)
public abstract class MixinIngredientListOverlay {
	@Unique private static final int CG_BUTTON_GAP = 2;

	@Shadow private IconButton configButton;
	@Shadow private GuiTextFieldFilter searchField;
	@Shadow public abstract boolean isListDisplayed();

	@Unique private IconButton cg$groupsButton;
	@Unique private int cg$lastGuiWidth = Integer.MIN_VALUE;
	@Unique private int cg$lastGuiHeight = Integer.MIN_VALUE;
	@Unique private String cg$lastSearchText;
	@Unique private ImmutableRect2i cg$lastSearchArea;

	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void cg$onInit(CallbackInfo ci) {
		this.cg$groupsButton = new IconButton(new GroupsButtonController());
		GroupBackgroundRenderer.clear();
	}

	/**
	 * Frame-synced button placement. JEI's internal layout entry point is not stable
	 * across releases (19.32 removed {@code IngredientListOverlay.updateBounds} and its
	 * replacement is still drifting), so instead of injecting into layout we re-derive
	 * our bounds from the config button's current area right before drawing.
	 * The shadowed fields and {@code drawScreen} exist across all supported JEI versions.
	 */
	@Unique
	private void cg$syncBoundsToConfigButton(boolean showGroupsButton) {
		ImmutableRect2i configArea = this.configButton.getArea();
		if (configArea == null || configArea.isEmpty()) return;

		ImmutableRect2i searchArea = ((MixinGuiTextFieldFilterAccessor) (Object) searchField).cg$getArea();
		if (searchArea == null || searchArea.isEmpty()) return;

		ViewerOverlayHook.Bounds buttonBounds = new ViewerOverlayHook.Bounds(
			configArea.getX(), configArea.getY(), configArea.getWidth(), configArea.getHeight());
		if (showGroupsButton) {
			buttonBounds = JeiViewerAdapter.instance().overlayHook().placeButton(buttonBounds, CG_BUTTON_GAP);
			ImmutableRect2i groupsArea = new ImmutableRect2i(
				buttonBounds.x(), buttonBounds.y(), buttonBounds.width(), buttonBounds.height());
			if (!groupsArea.equals(this.cg$groupsButton.getArea())) {
				this.cg$groupsButton.updateBounds(groupsArea);
			}
		}

		int adjustedWidth = JeiViewerAdapter.instance().overlayHook().adjustSearchFieldWidth(
			new ViewerOverlayHook.Bounds(searchArea.getX(), searchArea.getY(), searchArea.getWidth(), searchArea.getHeight()),
			buttonBounds,
			CG_BUTTON_GAP);
		if (searchArea.getWidth() != adjustedWidth) {
			this.searchField.updateBounds(new ImmutableRect2i(
				searchArea.getX(), searchArea.getY(), adjustedWidth, searchArea.getHeight()));
		}
	}

	@Inject(method = "drawScreen", at = @At("HEAD"), require = 0)
	private void cg$syncBoundsBeforeDraw(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		if (!this.isListDisplayed()) {
			GroupBackgroundRenderer.clear();
			cg$resetBackgroundTracking();
			return;
		}
		cg$syncBoundsToConfigButton(cg$shouldShowGroupsButton());
		String searchText = this.searchField.getValue();
		ImmutableRect2i searchArea = ((MixinGuiTextFieldFilterAccessor) (Object) searchField).cg$getArea();
		int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		if (guiWidth != this.cg$lastGuiWidth
			|| guiHeight != this.cg$lastGuiHeight
			|| !Objects.equals(searchText, this.cg$lastSearchText)
			|| !Objects.equals(searchArea, this.cg$lastSearchArea)) {
			GroupBackgroundRenderer.clear();
		}
		this.cg$lastGuiWidth = guiWidth;
		this.cg$lastGuiHeight = guiHeight;
		this.cg$lastSearchText = searchText;
		this.cg$lastSearchArea = searchArea;
		GroupBackgroundRenderer.renderPreviousFrame(guiGraphics, Services.CONFIG.showGroupBackgrounds());
	}

	@Inject(method = "drawScreen", at = @At("TAIL"), require = 0)
	private void cg$drawScreen(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		GroupBorderRenderer.renderAndClear(guiGraphics);
		GroupBackgroundRenderer.advanceFrame();
		if (cg$shouldShowGroupsButton()) this.cg$groupsButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
	}

	@Inject(method = "drawTooltips", at = @At("TAIL"), require = 0)
	private void cg$drawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (cg$shouldShowGroupsButton()) this.cg$groupsButton.drawTooltips(guiGraphics, mouseX, mouseY);
	}

	@Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true, require = 0)
	private void cg$wrapInputHandler(CallbackInfoReturnable<IUserInputHandler> cir) {
		if (this.cg$groupsButton == null) return;
		IUserInputHandler original = cir.getReturnValue();
		IUserInputHandler groupsHandler = this.cg$groupsButton.createInputHandler();
		IUserInputHandler combined = new CombinedInputHandler("IngredientListOverlay_withGroups", groupsHandler, original);
		cir.setReturnValue(new ProxyInputHandler(() -> cg$shouldShowGroupsButton() ? combined : original));
	}

	@Unique
	private void cg$resetBackgroundTracking() {
		this.cg$lastGuiWidth = Integer.MIN_VALUE;
		this.cg$lastGuiHeight = Integer.MIN_VALUE;
		this.cg$lastSearchText = null;
		this.cg$lastSearchArea = null;
	}

	@Unique
	private boolean cg$shouldShowGroupsButton() {
		return cg$groupsButton != null && JeiViewerAdapter.instance().overlayHook().shouldShowButton(
			Services.CONFIG.showManagerButton(), this.isListDisplayed());
	}
}
