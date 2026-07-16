package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchDocument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record EditorFluidIngredientView(
	Object ingredient,
	Component displayName,
	String resourceId,
	IngredientSearchDocument searchDocument,
	ItemStack fallbackBucket
) {}
