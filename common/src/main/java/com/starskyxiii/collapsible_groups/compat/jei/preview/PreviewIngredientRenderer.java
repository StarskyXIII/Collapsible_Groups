package com.starskyxiii.collapsible_groups.compat.jei.preview;

import com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientRenderBridge;
import com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientTypes;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import com.starskyxiii.collapsible_groups.platform.Services;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class PreviewIngredientRenderer {
	private PreviewIngredientRenderer() {}

	public static Object getFluidIngredient(ITypedIngredient<?> typed) {
		IIngredientType<?> fluidType = JeiIngredientTypes.getFluidType();
		if (fluidType == null) return null;
		return getIngredient(typed, fluidType);
	}

	static void renderFluid(GuiGraphics guiGraphics, Object fluid, int x, int y) {
		IIngredientType<?> fluidType = JeiIngredientTypes.getFluidType();
		if (JeiRuntimeHolder.get() != null && fluidType != null) {
			renderWithJei(guiGraphics, fluidType, fluid, x, y);
			return;
		}

		ItemStack fallback = Services.PLATFORM.getFluidFallbackBucket(fluid);
		if (fallback != null && !fallback.isEmpty()) {
			guiGraphics.renderItem(fallback, x, y);
		}
	}

	static void renderGeneric(GuiGraphics guiGraphics, IIngredientType<Object> type, Object ingredient, int x, int y) {
		if (JeiRuntimeHolder.get() == null) return;
		renderWithJei(guiGraphics, type, ingredient, x, y);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Object getIngredient(ITypedIngredient<?> typed, IIngredientType type) {
		return typed.getIngredient(type).orElse(null);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void renderWithJei(GuiGraphics guiGraphics, IIngredientType type, Object ingredient, int x, int y) {
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null) return;
		IIngredientRenderer renderer = runtime.getIngredientManager().getIngredientRenderer(type);
		JeiIngredientRenderBridge.render(guiGraphics, renderer, ingredient, x, y);
	}
}
