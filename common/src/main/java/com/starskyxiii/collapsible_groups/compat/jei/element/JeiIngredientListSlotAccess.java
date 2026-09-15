package com.starskyxiii.collapsible_groups.compat.jei.element;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.elements.IElement;

import java.util.Optional;

public interface JeiIngredientListSlotAccess {
	Optional<IElement<?>> getOptionalElement();
	ImmutableRect2i getRenderArea();
}
