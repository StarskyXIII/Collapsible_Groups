package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.element.JeiClickableElement;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.overlay.elements.IElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IElement.class, remap = false)
public interface MixinJeiElement {
	@Inject(
		method = {
			"handleClick(Lmezz/jei/gui/input/UserInput;Lmezz/jei/common/input/IInternalKeyMappings;)Z",
			"handleClick(Lmezz/jei/common/input/UserInput;Lmezz/jei/common/input/IInternalKeyMappings;)Z"
		},
		at = @At("HEAD"),
		cancellable = true,
		require = 1,
		allow = 1
	)
	private void cg$handleClick(@Coerce IJeiUserInput input, IInternalKeyMappings keyBindings,
		CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof JeiClickableElement element) {
			cir.setReturnValue(element.handleJeiClick(input, keyBindings));
		}
	}
}
