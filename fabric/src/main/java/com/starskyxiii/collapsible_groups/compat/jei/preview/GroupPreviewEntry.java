package com.starskyxiii.collapsible_groups.compat.jei.preview;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight renderable preview entry that can represent an item, Fabric fluid,
 * or arbitrary JEI ingredient for mixed group previews.
 */
public final class GroupPreviewEntry {
	private final ItemStack item;
	private final IJeiFluidIngredient fluid;
	private final IIngredientType<Object> genericType;
	private final Object genericIngredient;

	private GroupPreviewEntry(ItemStack item, IJeiFluidIngredient fluid, IIngredientType<Object> genericType, Object genericIngredient) {
		this.item = item;
		this.fluid = fluid;
		this.genericType = genericType;
		this.genericIngredient = genericIngredient;
	}

	public static GroupPreviewEntry ofItem(ItemStack stack) {
		return new GroupPreviewEntry(stack, null, null, null);
	}

	public static GroupPreviewEntry ofFluid(IJeiFluidIngredient stack) {
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
			renderFluid(guiGraphics, fluid, x, y);
			return;
		}
		if (genericType != null && genericIngredient != null) {
			renderGeneric(guiGraphics, genericType, genericIngredient, x, y);
		}
	}

	public static List<GroupPreviewEntry> fromItems(List<ItemStack> items) {
		List<GroupPreviewEntry> result = new ArrayList<>(items.size());
		for (ItemStack item : items) result.add(ofItem(item));
		return List.copyOf(result);
	}

	public static List<GroupPreviewEntry> fromFluids(List<Object> fluids) {
		List<GroupPreviewEntry> result = new ArrayList<>(fluids.size());
		for (Object fluid : fluids) {
			result.add(ofFluid((IJeiFluidIngredient) fluid));
		}
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
			if (typed.getItemStack().isPresent()) {
				result.add(ofItem(typed.getItemStack().orElseThrow()));
				continue;
			}
			if (typed.getIngredient(FabricTypes.FLUID_STACK).isPresent()) {
				result.add(ofFluid(typed.getIngredient(FabricTypes.FLUID_STACK).orElseThrow()));
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

	private static void renderFluid(GuiGraphics guiGraphics, IJeiFluidIngredient fluid, int x, int y) {
		var runtime = JeiRuntimeHolder.get();
		if (runtime != null) {
			var renderer = runtime.getIngredientManager().getIngredientRenderer(FabricTypes.FLUID_STACK);
			guiGraphics.pose().pushPose();
			guiGraphics.pose().translate(x, y, 0);
			renderer.render(guiGraphics, fluid);
			guiGraphics.pose().popPose();
			return;
		}

		var bucket = fluid.getFluidVariant().getFluid().getBucket();
		if (bucket != Items.AIR) {
			guiGraphics.renderItem(new ItemStack(bucket), x, y);
		}
	}

	private static void renderGeneric(GuiGraphics guiGraphics, IIngredientType<Object> type, Object ingredient, int x, int y) {
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null) return;
		IIngredientRenderer<Object> renderer = runtime.getIngredientManager().getIngredientRenderer(type);
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(x, y, 0);
		renderer.render(guiGraphics, ingredient);
		guiGraphics.pose().popPose();
	}
}
