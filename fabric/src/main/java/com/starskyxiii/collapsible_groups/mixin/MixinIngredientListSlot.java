package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.element.JeiIngredientListSlotAccess;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.elements.IElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Pseudo
@Mixin(targets = {
	"mezz.jei.gui.overlay.IngredientListSlot",
	"mezz.jei.gui.overlay.ingredients.IngredientListSlot"
}, remap = false)
public abstract class MixinIngredientListSlot implements JeiIngredientListSlotAccess {
	@Shadow public abstract Optional<IElement<?>> getOptionalElement();
	@Shadow public abstract ImmutableRect2i getRenderArea();
}
