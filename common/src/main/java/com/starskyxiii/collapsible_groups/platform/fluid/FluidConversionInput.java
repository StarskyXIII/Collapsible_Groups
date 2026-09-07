package com.starskyxiii.collapsible_groups.platform.fluid;

import java.util.Objects;

public record FluidConversionInput(Object nativeValue, FluidAmount amount) {
	public FluidConversionInput {
		Objects.requireNonNull(nativeValue, "nativeValue");
		Objects.requireNonNull(amount, "amount");
	}
}
