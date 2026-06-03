package com.starskyxiii.collapsible_groups.compat.jei.editor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

record EditorFluidIngredientView(
	Object ingredient,
	Component displayName,
	String resourceId,
	String searchKey,
	ItemStack fallbackBucket
) {}
