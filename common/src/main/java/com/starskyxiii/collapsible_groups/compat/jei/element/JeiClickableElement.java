package com.starskyxiii.collapsible_groups.compat.jei.element;

import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.common.input.IInternalKeyMappings;

public interface JeiClickableElement {
	boolean handleJeiClick(IJeiUserInput input, IInternalKeyMappings keyBindings);
}
