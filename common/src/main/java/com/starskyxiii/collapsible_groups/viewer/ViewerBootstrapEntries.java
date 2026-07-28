package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Type-safe neutral payloads used by scripting bootstrap integrations. */
public final class ViewerBootstrapEntries {
	private ViewerBootstrapEntries() {}

	public static List<ItemStack> itemStacks(ViewerBootstrapContext<?> context) {
		return context.universe().items().stream().map(ViewerIngredient::view)
			.filter(ItemStackIngredientView.class::isInstance).map(ItemStackIngredientView.class::cast)
			.map(ItemStackIngredientView::stack).toList();
	}

	public static List<ResourceLocation> resourceIds(ViewerBootstrapContext<?> context,
		ViewerIngredient.Kind kind) {
		return context.universe().ordered().stream().filter(value -> value.kind() == kind)
			.map(value -> value.view().resourceLocation()).filter(java.util.Objects::nonNull).toList();
	}
}
