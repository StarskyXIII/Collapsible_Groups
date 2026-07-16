package com.starskyxiii.collapsible_groups.mixin;

// Keep this accessor byte-identical across Fabric, Forge, and NeoForge.

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.GuiTextFieldFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiTextFieldFilter.class, remap = false)
public interface MixinGuiTextFieldFilterAccessor {
	@Accessor("area")
	ImmutableRect2i cg$getArea();
}
