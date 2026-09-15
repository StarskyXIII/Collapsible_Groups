package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.element.JeiIngredientListSlotAccess;
import com.starskyxiii.collapsible_groups.compat.jei.element.PreRenderIngredientGridElement;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = {
	"mezz.jei.gui.overlay.IngredientListRenderer",
	"mezz.jei.gui.overlay.ingredients.IngredientListRenderer"
}, remap = false)
public abstract class MixinIngredientListRenderer {
	@Shadow
	@Final
	private List<?> slots;

	@Inject(method = "render", at = @At("HEAD"), require = 1)
	private void cg$drawPreRenderBackgrounds(GuiGraphics guiGraphics, CallbackInfo ci) {
		for (Object value : this.slots) {
			var slot = (JeiIngredientListSlotAccess) value;
			slot.getOptionalElement().ifPresent(element -> {
				if (element instanceof PreRenderIngredientGridElement preRenderElement) {
					var renderArea = slot.getRenderArea();
					preRenderElement.drawPreRender(guiGraphics, renderArea.getX(), renderArea.getY());
				}
			});
		}
	}
}
