package com.starskyxiii.collapsible_groups.platform.fluid;

import java.util.Objects;

public record FluidAmount(long value, FluidAmountUnit unit) {
	public FluidAmount {
		if (value < 0) throw new IllegalArgumentException("Fluid amount must not be negative");
		Objects.requireNonNull(unit, "unit");
	}
}
