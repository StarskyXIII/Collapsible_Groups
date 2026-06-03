package com.starskyxiii.collapsible_groups.compat.jei.preview;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight renderable preview entry that can represent an item, fluid, or
 * arbitrary JEI ingredient for mixed group previews.
 */
public final class GroupPreviewEntry {
	private final ItemStack item;
	private final Object fluid;
	private final IIngredientType<Object> genericType;
	private final Object genericIngredient;

	private GroupPreviewEntry(ItemStack item, Object fluid, IIngredientType<Object> genericType, Object genericIngredient) {
		this.item = item;
		this.fluid = fluid;
		this.genericType = genericType;
		this.genericIngredient = genericIngredient;
	}

	public static GroupPreviewEntry ofItem(ItemStack stack) {
		return new GroupPreviewEntry(stack, null, null, null);
	}

	public static GroupPreviewEntry ofFluid(Object stack) {
		return new GroupPreviewEntry(null, stack, null, null);
	}

	@SuppressWarnings("unchecked")
	public static <T> GroupPreviewEntry ofGeneric(IIngredientType<T> type, T ingredient) {
		return new GroupPreviewEntry(null, null, (IIngredientType<Object>) type, ingredient);
	}

	public void render(GuiGraphics guiGraphics, int x, int y) {
		if (item != null) {
			guiGraphics.renderItem(item, x, y);
			return;
		}
		if (fluid != null) {
			PreviewIngredientRenderer.renderFluid(guiGraphics, fluid, x, y);
			return;
		}
		if (genericType != null && genericIngredient != null) {
			PreviewIngredientRenderer.renderGeneric(guiGraphics, genericType, genericIngredient, x, y);
		}
	}

	public static List<GroupPreviewEntry> fromItems(List<ItemStack> items) {
		List<GroupPreviewEntry> result = new ArrayList<>(items.size());
		for (ItemStack item : items) result.add(ofItem(item));
		return List.copyOf(result);
	}

	public static List<GroupPreviewEntry> fromFluids(List<Object> fluids) {
		List<GroupPreviewEntry> result = new ArrayList<>(fluids.size());
		for (Object fluid : fluids) result.add(ofFluid(fluid));
		return List.copyOf(result);
	}

	public static List<GroupPreviewEntry> fromGenericRefs(List<GenericIngredientRef> refs) {
		List<GroupPreviewEntry> result = new ArrayList<>(refs.size());
		for (GenericIngredientRef ref : refs) {
			result.add(ofGeneric(ref.type(), ref.ingredient()));
		}
		return List.copyOf(result);
	}

	@SuppressWarnings("unchecked")
	public static List<GroupPreviewEntry> fromTypedIngredients(List<ITypedIngredient<?>> typedIngredients) {
		List<GroupPreviewEntry> result = new ArrayList<>(typedIngredients.size());
		for (ITypedIngredient<?> typed : typedIngredients) {
			var itemStack = typed.getItemStack();
			if (itemStack.isPresent()) {
				result.add(ofItem(itemStack.orElseThrow()));
				continue;
			}
			Object fluid = PreviewIngredientRenderer.getFluidIngredient(typed);
			if (fluid != null) {
				result.add(ofFluid(fluid));
				continue;
			}
			result.add(ofGeneric((IIngredientType<Object>) typed.getType(), typed.getIngredient()));
		}
		return List.copyOf(result);
	}

	public static List<GroupPreviewEntry> combine(
		List<ItemStack> items,
		List<Object> fluids,
		List<GenericIngredientRef> genericRefs
	) {
		List<GroupPreviewEntry> result = new ArrayList<>(items.size() + fluids.size() + genericRefs.size());
		result.addAll(fromItems(items));
		result.addAll(fromFluids(fluids));
		result.addAll(fromGenericRefs(genericRefs));
		return List.copyOf(result);
	}
}
