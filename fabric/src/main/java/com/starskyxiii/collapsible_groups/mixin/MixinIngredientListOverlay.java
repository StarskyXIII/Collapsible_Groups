package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiIngredientListOverlayController;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.gui.input.GuiTextFieldFilter;
import mezz.jei.gui.overlay.IngredientListOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IngredientListOverlay.class, remap = false)
public abstract class MixinIngredientListOverlay {
	@Shadow private IconButton configButton;
	@Shadow private GuiTextFieldFilter searchField;
	@Shadow private boolean isSearchDisplayed() { throw new AssertionError(); }
	@Unique private JeiIngredientListOverlayController cg$controller;

	@Inject(
		method = "<init>",
		at = @At("TAIL"),
		require = 1
	)
	private void cg$onInit(CallbackInfo ci) {
		if (!ViewerLifecycleCoordinator.isJeiSelected()) return;
		this.cg$controller = new JeiIngredientListOverlayController(
			this.configButton, this.searchField,
			this::isSearchDisplayed, Services.CONFIG::showManagerButton);
	}

	@Inject(
		method = "drawBackground",
		at = @At("HEAD"),
		require = 1
	)
	private void cg$drawBackgroundPhase(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (!ViewerLifecycleCoordinator.isJeiSelected() || this.cg$controller == null) return;
		this.cg$controller.drawBackgroundPhase(graphics);
	}

	@Inject(
		method = "drawForeground",
		at = @At("RETURN"),
		require = 1
	)
	private void cg$drawForegroundPhase(Minecraft minecraft, GuiGraphicsExtractor graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci) {
		if (!ViewerLifecycleCoordinator.isJeiSelected() || this.cg$controller == null) return;
		this.cg$controller.drawForegroundPhase(graphics, mouseX, mouseY, partialTicks);
	}

	@Inject(
		method = "drawTooltips",
		at = @At("RETURN"),
		require = 1
	)
	private void cg$drawTooltips(Minecraft minecraft, GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (!ViewerLifecycleCoordinator.isJeiSelected() || this.cg$controller == null) return;
		this.cg$controller.drawTooltips(graphics, mouseX, mouseY);
	}

	// Wrap controls at their caller: optimized createInputHandler bytecode can reuse the this slot.
	@ModifyArg(
		method = "createInputArea(Lmezz/jei/common/input/IInternalKeyMappings;)Lmezz/jei/gui/input/InputArea;",
		at = @At(value = "INVOKE", target = "Lmezz/jei/gui/input/InputArea$Builder;controls(Lmezz/jei/common/input/IInputTarget;)Lmezz/jei/gui/input/InputArea$Builder;"),
		index = 0,
		require = 1,
		allow = 1
	)
	private IInputTarget cg$wrapInputHandler(IInputTarget original) {
		if (!ViewerLifecycleCoordinator.isJeiSelected() || this.cg$controller == null) return original;
		return this.cg$controller.wrapInputHandler(original);
	}

	@Inject(method = "isMouseOver(DD)Z", at = @At("HEAD"), cancellable = true, require = 1)
	private void cg$includeGroupsButton(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
		if (ViewerLifecycleCoordinator.isJeiSelected() && this.cg$controller != null
			&& this.cg$controller.isMouseOver(mouseX, mouseY)) {
			cir.setReturnValue(true);
		}
	}
}
