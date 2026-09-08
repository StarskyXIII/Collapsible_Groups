package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiIngredientListOverlayController;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.input.GuiTextFieldFilter;
import mezz.jei.gui.input.IUserInputHandler;
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
	@Shadow private GuiIconToggleButton configButton;
	@Shadow private GuiTextFieldFilter searchField;
	@Shadow public abstract boolean isListDisplayed();
	@Unique private JeiIngredientListOverlayController cg$controller;

	@Inject(
		method = "<init>(Lmezz/jei/gui/overlay/IIngredientGridSource;Lmezz/jei/gui/filter/IFilterTextSource;" +
			"Lmezz/jei/api/runtime/IScreenHelper;Lmezz/jei/gui/overlay/IngredientGridWithNavigation;" +
			"Lmezz/jei/common/config/IClientConfig;" +
			"Lmezz/jei/common/config/IClientToggleState;Lmezz/jei/common/input/IInternalKeyMappings;)V",
		at = @At("TAIL"),
		require = 1
	)
	private void cg$onInit(CallbackInfo ci) {
		if (!ViewerLifecycleCoordinator.isJeiSelected()) return;
		this.cg$controller = new JeiIngredientListOverlayController(
			() -> ((MixinGuiIconToggleButtonAccessor) (Object) this.configButton).cg$getArea(), this.searchField,
			() -> ((MixinGuiTextFieldFilterAccessor) (Object) this.searchField).cg$getArea(),
			this::isListDisplayed, Services.CONFIG::showManagerButton);
	}

	@Inject(
		method = "drawScreen",
		at = @At("HEAD"),
		require = 1
	)
	private void cg$drawBackgroundPhase(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci) {
		if (!ViewerLifecycleCoordinator.isJeiSelected() || this.cg$controller == null) return;
		this.cg$controller.drawBackgroundPhase(graphics);
	}

	@Inject(
		method = "drawScreen",
		at = @At("TAIL"),
		require = 1
	)
	private void cg$drawForegroundPhase(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci) {
		if (!ViewerLifecycleCoordinator.isJeiSelected() || this.cg$controller == null) return;
		this.cg$controller.drawForegroundPhase(graphics, mouseX, mouseY, partialTicks);
	}

	@Inject(
		method = "drawTooltips",
		at = @At("TAIL"),
		require = 1
	)
	private void cg$drawTooltips(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (!ViewerLifecycleCoordinator.isJeiSelected() || this.cg$controller == null) return;
		this.cg$controller.drawTooltips(graphics, mouseX, mouseY);
	}

	@Inject(
		method = "createInputHandler()Lmezz/jei/gui/input/IUserInputHandler;",
		at = @At("RETURN"),
		cancellable = true,
		require = 1
	)
	private void cg$wrapInputHandler(CallbackInfoReturnable<IUserInputHandler> cir) {
		if (!ViewerLifecycleCoordinator.isJeiSelected() || this.cg$controller == null) return;
		cir.setReturnValue(this.cg$controller.wrapInputHandler(cir.getReturnValue()));
	}
}
