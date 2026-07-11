package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.core.IngredientSearchDocument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

record EditorFluidIngredientView(
	Object ingredient,
	Component displayName,
	String resourceId,
	IngredientSearchDocument searchDocument,
	ItemStack fallbackBucket
) {}
