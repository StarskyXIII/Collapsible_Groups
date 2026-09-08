package com.starskyxiii.collapsible_groups.platform.fluid;

import java.util.Objects;

public sealed interface FluidConversionResult permits FluidConversionResult.Success, FluidConversionResult.Unsupported {
	record Success(FluidIngredient ingredient) implements FluidConversionResult {
		public Success {
			Objects.requireNonNull(ingredient, "ingredient");
		}
	}

	record Unsupported(String reason) implements FluidConversionResult {
		public Unsupported {
			Objects.requireNonNull(reason, "reason");
			if (reason.isBlank()) throw new IllegalArgumentException("Unsupported reason must not be blank");
		}
	}

	default FluidIngredient require() {
		if (this instanceof Success) return ((Success) this).ingredient();
		throw new IllegalArgumentException(((Unsupported) this).reason());
	}
}
