package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.platform.fluid.FluidIngredient;

import java.util.Objects;

public record JeiFluidIngredient(Object viewerValue, FluidIngredient fluid) {
	public JeiFluidIngredient {
		Objects.requireNonNull(viewerValue, "viewerValue");
		Objects.requireNonNull(fluid, "fluid");
	}
}
