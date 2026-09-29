package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiElementInputBridge;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.overlay.elements.IElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.input.handlers.ElementInputHandler", remap = false)
public abstract class MixinElementInputHandler {
	@Redirect(
		method = "handleUserInput",
		at = @At(value = "INVOKE", target = "Lmezz/jei/gui/overlay/elements/IElement;handleClick(Lmezz/jei/common/input/UserInput;Lmezz/jei/common/input/IInternalKeyMappings;)Z"),
		require = 1,
		allow = 1
	)
	private boolean cg$handleElementClick(IElement<?> element, @Coerce IJeiUserInput input,
		IInternalKeyMappings keys) {
		return JeiElementInputBridge.handleClick(element, input, keys);
	}
}
