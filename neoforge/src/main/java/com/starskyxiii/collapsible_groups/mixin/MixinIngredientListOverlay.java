package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiIngredientListOverlayController;
import com.starskyxiii.collapsible_groups.config.NeoForgeConfig;
import mezz.jei.gui.elements.IconButton;
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
	@Shadow private IconButton configButton;
	@Shadow private GuiTextFieldFilter searchField;
	@Shadow public abstract boolean isListDisplayed();
	@Unique private JeiIngredientListOverlayController cg$controller;

	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void cg$onInit(CallbackInfo ci) {
		this.cg$controller = new JeiIngredientListOverlayController(
			this.configButton, this.searchField,
			() -> ((MixinGuiTextFieldFilterAccessor) (Object) this.searchField).cg$getArea(),
			this::isListDisplayed, () -> NeoForgeConfig.SHOW_MANAGER_BUTTON.get());
	}

	@Inject(method = "drawScreen", at = @At("HEAD"), require = 0)
	private void cg$beforeDraw(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci) {
		this.cg$controller.beforeDraw(graphics);
	}

	@Inject(method = "drawScreen", at = @At("TAIL"), require = 0)
	private void cg$afterDraw(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci) {
		this.cg$controller.afterDraw(graphics, mouseX, mouseY, partialTicks);
	}

	@Inject(method = "drawTooltips", at = @At("TAIL"), require = 0)
	private void cg$drawTooltips(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
		this.cg$controller.drawTooltips(graphics, mouseX, mouseY);
	}

	@Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true, require = 0)
	private void cg$wrapInputHandler(CallbackInfoReturnable<IUserInputHandler> cir) {
		if (this.cg$controller == null) return;
		cir.setReturnValue(this.cg$controller.wrapInputHandler(cir.getReturnValue()));
	}
}
