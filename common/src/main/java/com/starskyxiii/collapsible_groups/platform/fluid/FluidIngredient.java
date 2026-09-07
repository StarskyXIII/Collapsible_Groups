package com.starskyxiii.collapsible_groups.platform.fluid;

import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public record FluidIngredient(
	Object nativeValue,
	FluidAmount amount,
	ResourceLocation id,
	Component displayName,
	ItemStack fallbackBucket,
	IngredientView view
) {
	public FluidIngredient {
		Objects.requireNonNull(nativeValue, "nativeValue");
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(fallbackBucket, "fallbackBucket");
		Objects.requireNonNull(view, "view");
	}
}
