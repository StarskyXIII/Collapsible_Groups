package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.config.NeoForgeConfig;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBorderRenderer;
import com.starskyxiii.collapsible_groups.compat.jei.manager.GroupsButtonController;
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

@Mixin(value = IngredientListOverlay.class, remap = false)
public abstract class MixinIngredientListOverlay {
	@Unique
	private static final int CG_BUTTON_GAP = 2;

	@Shadow
	private IconButton configButton;

	@Shadow
	private GuiTextFieldFilter searchField;

	@Shadow
	public abstract boolean isListDisplayed();

	@Unique
	private IconButton cg$groupsButton;

	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void cg$onInit(CallbackInfo ci) {
		this.cg$groupsButton = new IconButton(new GroupsButtonController());
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

		int desiredSearchRight = configArea.getX() - CG_BUTTON_GAP;
		if (showGroupsButton) {
			// Place groups button immediately to the left of the config button (with 2px gap)
			ImmutableRect2i groupsArea = new ImmutableRect2i(
				configArea.getX() - configArea.getWidth() - CG_BUTTON_GAP,
				configArea.getY(),
				configArea.getWidth(),
				configArea.getHeight()
			);
			if (!groupsArea.equals(this.cg$groupsButton.getArea())) {
				this.cg$groupsButton.updateBounds(groupsArea);
			}
			desiredSearchRight = groupsArea.getX() - CG_BUTTON_GAP;
		}

		int adjustedWidth = Math.max(0, desiredSearchRight - searchArea.getX());
		if (searchArea.getWidth() != adjustedWidth) {
			this.searchField.updateBounds(new ImmutableRect2i(
				searchArea.getX(),
				searchArea.getY(),
				adjustedWidth,
				searchArea.getHeight()
			));
		}
	}

	@Inject(method = "drawScreen", at = @At("HEAD"), require = 0)
	private void cg$syncBoundsBeforeDraw(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		cg$syncBoundsToConfigButton(cg$shouldShowGroupsButton());
	}

	@Inject(method = "drawScreen", at = @At("TAIL"), require = 0)
	private void cg$drawScreen(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		GroupBorderRenderer.renderAndClear(guiGraphics);
		if (cg$shouldShowGroupsButton()) this.cg$groupsButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
	}

	@Inject(method = "drawTooltips", at = @At("TAIL"), require = 0)
	private void cg$drawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (cg$shouldShowGroupsButton()) {
			this.cg$groupsButton.drawTooltips(guiGraphics, mouseX, mouseY);
		}
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
	private boolean cg$shouldShowGroupsButton() {
		return cg$groupsButton != null && NeoForgeConfig.SHOW_MANAGER_BUTTON.get() && this.isListDisplayed();
	}
}
