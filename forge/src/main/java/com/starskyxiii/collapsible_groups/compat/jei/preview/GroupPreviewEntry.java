package com.starskyxiii.collapsible_groups.compat.jei.preview;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight renderable preview entry that can represent an item or fluid.
 */
public final class GroupPreviewEntry {
	private final ItemStack item;
	private final FluidStack fluid;

	private GroupPreviewEntry(ItemStack item, FluidStack fluid) {
		this.item = item;
		this.fluid = fluid;
	}

	public static GroupPreviewEntry ofItem(ItemStack stack) {
		return new GroupPreviewEntry(stack, null);
	}

	public static GroupPreviewEntry ofFluid(FluidStack stack) {
		return new GroupPreviewEntry(null, stack);
	}

	public void render(GuiGraphics guiGraphics, int x, int y) {
		if (item != null) {
			guiGraphics.renderItem(item, x, y);
			return;
		}
		if (fluid != null) {
			renderFluid(guiGraphics, fluid, x, y);
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
			result.add(ofFluid((FluidStack) fluid));
		}
		return List.copyOf(result);
	}

	public static List<GroupPreviewEntry> fromTypedIngredients(List<ITypedIngredient<?>> typedIngredients) {
		List<GroupPreviewEntry> result = new ArrayList<>(typedIngredients.size());
		for (ITypedIngredient<?> typed : typedIngredients) {
			typed.getItemStack().ifPresent(stack -> result.add(ofItem(stack)));
			typed.getIngredient(ForgeTypes.FLUID_STACK).ifPresent(stack -> result.add(ofFluid(stack)));
		}
		return List.copyOf(result);
	}

	public static List<GroupPreviewEntry> combine(List<ItemStack> items, List<Object> fluids) {
		List<GroupPreviewEntry> result = new ArrayList<>(items.size() + fluids.size());
		result.addAll(fromItems(items));
		result.addAll(fromFluids(fluids));
		return List.copyOf(result);
	}

	private static void renderFluid(GuiGraphics guiGraphics, FluidStack fluid, int x, int y) {
		var runtime = JeiRuntimeHolder.get();
		if (runtime != null) {
			var renderer = runtime.getIngredientManager().getIngredientRenderer(ForgeTypes.FLUID_STACK);
			guiGraphics.pose().pushPose();
			guiGraphics.pose().translate(x, y, 0);
			renderer.render(guiGraphics, fluid);
			guiGraphics.pose().popPose();
			return;
		}

		var bucket = fluid.getFluid().getBucket();
		if (bucket != Items.AIR) {
			guiGraphics.renderItem(new ItemStack(bucket), x, y);
		}
	}
}
