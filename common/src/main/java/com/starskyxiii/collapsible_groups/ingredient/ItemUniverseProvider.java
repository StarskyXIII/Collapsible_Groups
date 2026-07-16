package com.starskyxiii.collapsible_groups.ingredient;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Loader- and overlay-neutral source of item stacks shown by editor pickers.
 * Implementations may provide every known variant, not just one stack per item.
 */
@FunctionalInterface
public interface ItemUniverseProvider {
	List<ItemStack> allStacks();
}
