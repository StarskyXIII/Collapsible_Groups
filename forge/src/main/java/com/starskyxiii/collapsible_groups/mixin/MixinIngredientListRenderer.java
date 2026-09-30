package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.element.PreRenderIngredientGridElement;
import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = IngredientListRenderer.class, remap = false)
public abstract class MixinIngredientListRenderer {
	@Shadow
	@Final
	private List<IngredientListSlot> slots;

	@Inject(
		method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V",
		at = @At("HEAD"),
		require = 1
	)
	private void cg$drawPreRenderBackgrounds(GuiGraphics guiGraphics, CallbackInfo ci) {
		for (IngredientListSlot slot : this.slots) {
			slot.getOptionalElement().ifPresent(element -> {
				if (element instanceof PreRenderIngredientGridElement preRenderElement) {
					var renderArea = slot.getRenderArea();
					preRenderElement.drawPreRender(guiGraphics, renderArea.getX(), renderArea.getY());
				}
			});
		}
	}
}
