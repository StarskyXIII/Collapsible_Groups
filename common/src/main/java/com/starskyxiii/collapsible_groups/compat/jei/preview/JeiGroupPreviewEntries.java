package com.starskyxiii.collapsible_groups.compat.jei.preview;

import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** JEI-only conversion boundary for the otherwise viewer-neutral preview entry. */
public final class JeiGroupPreviewEntries {
	private JeiGroupPreviewEntries() {}

	public static GroupPreviewEntry ofFluid(Object stack) {
		return GroupPreviewEntry.ofRenderer(
			(graphics, x, y) -> PreviewIngredientRenderer.renderFluid(graphics, stack, x, y));
	}

	@SuppressWarnings("unchecked")
	public static <T> GroupPreviewEntry ofGeneric(IIngredientType<T> type, T ingredient) {
		IIngredientType<Object> erased = (IIngredientType<Object>) type;
		return GroupPreviewEntry.ofRenderer(
			(graphics, x, y) -> PreviewIngredientRenderer.renderGeneric(graphics, erased, ingredient, x, y));
	}

	public static List<GroupPreviewEntry> fromFluids(List<Object> fluids) {
		return fluids.stream().map(JeiGroupPreviewEntries::ofFluid).toList();
	}

	public static List<GroupPreviewEntry> fromGenericRefs(List<GenericIngredientRef> refs) {
		return refs.stream().map(ref -> ofGeneric(ref.type(), ref.ingredient())).toList();
	}

	@SuppressWarnings("unchecked")
	public static List<GroupPreviewEntry> fromTypedIngredients(List<ITypedIngredient<?>> typedIngredients) {
		List<GroupPreviewEntry> result = new ArrayList<>(typedIngredients.size());
		for (ITypedIngredient<?> typed : typedIngredients) {
			var itemStack = typed.getItemStack();
			if (itemStack.isPresent()) {
				result.add(GroupPreviewEntry.ofItem(itemStack.orElseThrow()));
				continue;
			}
			Object fluid = PreviewIngredientRenderer.getFluidIngredient(typed);
			if (fluid != null) result.add(ofFluid(fluid));
			else result.add(ofGeneric((IIngredientType<Object>) typed.getType(), typed.getIngredient()));
		}
		return List.copyOf(result);
	}

	public static List<GroupPreviewEntry> combine(List<ItemStack> items, List<Object> fluids,
		List<GenericIngredientRef> genericRefs) {
		List<GroupPreviewEntry> result = new ArrayList<>(items.size() + fluids.size() + genericRefs.size());
		result.addAll(GroupPreviewEntry.fromItems(items));
		result.addAll(fromFluids(fluids));
		result.addAll(fromGenericRefs(genericRefs));
		return List.copyOf(result);
	}
}
