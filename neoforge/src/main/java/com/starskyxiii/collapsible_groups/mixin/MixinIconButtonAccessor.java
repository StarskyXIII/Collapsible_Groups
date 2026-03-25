package com.starskyxiii.collapsible_groups.mixin;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.elements.IconButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = IconButton.class, remap = false)
public interface MixinIconButtonAccessor {
	@Accessor("area")
	ImmutableRect2i cg$getArea();
}
