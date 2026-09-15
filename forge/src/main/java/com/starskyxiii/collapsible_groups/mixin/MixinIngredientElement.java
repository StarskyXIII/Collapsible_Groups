package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.element.GroupHeaderElement;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.overlay.elements.IngredientElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IngredientElement.class, remap = false)
public abstract class MixinIngredientElement {
	@Inject(
		method = {
			"getTooltip(Lmezz/jei/common/gui/JeiTooltip;Lmezz/jei/gui/overlay/IngredientGridTooltipHelper;Lmezz/jei/api/ingredients/IIngredientRenderer;Lmezz/jei/api/ingredients/IIngredientHelper;)V",
			"getTooltip(Lmezz/jei/common/gui/JeiTooltip;Lmezz/jei/gui/overlay/ingredients/IngredientGridTooltipHelper;Lmezz/jei/api/ingredients/IIngredientRenderer;Lmezz/jei/api/ingredients/IIngredientHelper;)V"
		},
		at = @At("HEAD"),
		cancellable = true,
		require = 1,
		allow = 1
	)
	private void cg$appendHeaderTooltip(JeiTooltip tooltip, @Coerce Object tooltipHelper,
		IIngredientRenderer<?> renderer, IIngredientHelper<?> helper, CallbackInfo ci) {
		if ((Object) this instanceof GroupHeaderElement header) {
			header.appendTooltip(tooltip);
			ci.cancel();
		}
	}
}
