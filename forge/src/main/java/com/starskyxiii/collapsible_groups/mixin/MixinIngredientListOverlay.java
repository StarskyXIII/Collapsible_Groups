package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiIngredientListOverlayController;
import com.starskyxiii.collapsible_groups.platform.Services;
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

	@Inject(
		method = "<init>(Lmezz/jei/gui/overlay/ingredients/IIngredientGridSource;Lmezz/jei/gui/filter/IFilterTextSource;" +
			"Lmezz/jei/api/runtime/IScreenHelper;Lmezz/jei/gui/overlay/ingredients/IIngredientListOverlayContents;" +
			"Lmezz/jei/gui/overlay/bookmarks/history/LookupHistoryOverlay;" +
			"Lmezz/jei/common/config/IIngredientGridConfig;Lmezz/jei/common/config/IClientConfig;" +
			"Lmezz/jei/common/config/IClientToggleState;Lmezz/jei/common/input/IInternalKeyMappings;)V",
		at = @At("TAIL"),
		require = 1
	)
	private void cg$onInit(CallbackInfo ci) {
		this.cg$controller = new JeiIngredientListOverlayController(
			this.configButton, this.searchField,
			() -> ((MixinGuiTextFieldFilterAccessor) (Object) this.searchField).cg$getArea(),
			this::isListDisplayed, Services.CONFIG::showManagerButton);
	}

	@Inject(
		method = "drawBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
		at = @At("HEAD"),
		require = 1
	)
	private void cg$drawBackgroundPhase(GuiGraphics graphics, CallbackInfo ci) {
		this.cg$controller.drawBackgroundPhase(graphics);
	}

	@Inject(
		method = "drawForeground(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
		at = @At("TAIL"),
		require = 1
	)
	private void cg$drawForegroundPhase(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci) {
		this.cg$controller.drawForegroundPhase(graphics, mouseX, mouseY, partialTicks);
	}

	@Inject(
		method = "drawTooltips(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphics;II)V",
		at = @At("TAIL"),
		require = 1
	)
	private void cg$drawTooltips(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
		this.cg$controller.drawTooltips(graphics, mouseX, mouseY);
	}

	@Inject(
		method = "createInputHandler()Lmezz/jei/gui/input/IUserInputHandler;",
		at = @At("RETURN"),
		cancellable = true,
		require = 1
	)
	private void cg$wrapInputHandler(CallbackInfoReturnable<IUserInputHandler> cir) {
		if (this.cg$controller == null) return;
		cir.setReturnValue(this.cg$controller.wrapInputHandler(cir.getReturnValue()));
	}
}
