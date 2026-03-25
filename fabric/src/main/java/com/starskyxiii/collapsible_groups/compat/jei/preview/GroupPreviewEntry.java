package com.starskyxiii.collapsible_groups.compat.jei.preview;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Item-only preview entry for the Fabric loader.
 * Fluid/generic support is not available on this platform.
 */
public final class GroupPreviewEntry {
	private final ItemStack item;

	private GroupPreviewEntry(ItemStack item) {
		this.item = item;
	}

	public static GroupPreviewEntry ofItem(ItemStack stack) {
		return new GroupPreviewEntry(stack);
	}

	public void render(GuiGraphics guiGraphics, int x, int y) {
		if (item != null) {
			guiGraphics.renderItem(item, x, y);
		}
	}

	public static List<GroupPreviewEntry> fromItems(List<ItemStack> items) {
		List<GroupPreviewEntry> result = new ArrayList<>(items.size());
		for (ItemStack item : items) result.add(ofItem(item));
		return List.copyOf(result);
	}

	public static List<GroupPreviewEntry> fromTypedIngredients(List<ITypedIngredient<?>> typedIngredients) {
		List<GroupPreviewEntry> result = new ArrayList<>(typedIngredients.size());
		for (ITypedIngredient<?> typed : typedIngredients) {
			typed.getItemStack().ifPresent(stack -> result.add(ofItem(stack)));
		}
		return List.copyOf(result);
	}
}
