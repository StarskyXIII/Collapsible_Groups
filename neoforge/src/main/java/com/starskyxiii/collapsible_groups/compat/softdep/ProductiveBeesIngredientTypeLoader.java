package com.starskyxiii.collapsible_groups.compat.softdep;

import com.starskyxiii.collapsible_groups.compat.jei.api.CGApi;
import com.starskyxiii.collapsible_groups.Constants;
import mezz.jei.api.ingredients.IIngredientType;

/**
 * Registers Productive Bees' BeeIngredient type under "productivebees:bee".
 *
 * Uses reflection to avoid a compile-time dependency on Productive Bees.
 * Called only when ModList confirms Productive Bees is loaded.
 */
public final class ProductiveBeesIngredientTypeLoader {

	private ProductiveBeesIngredientTypeLoader() {}

	public static void register() {
		try {
			Class<?> jeiClass = Class.forName("cy.jdkdigital.productivebees.compat.jei.ProductiveBeesJeiPlugin");
			@SuppressWarnings("unchecked")
			IIngredientType<?> type = (IIngredientType<?>) jeiClass.getField("BEE_INGREDIENT").get(null);
			CGApi.registerIngredientType("productivebees:bee", type);
			CGApi.registerIngredientTypeAlias("bee", "productivebees:bee");
			Constants.LOG.info("[CollapsibleGroups] Registered Productive Bees ingredient type.");
		} catch (ClassNotFoundException e) {
			// Productive Bees JEI plugin class not present - safe to ignore
		} catch (Exception e) {
			Constants.LOG.warn("[CollapsibleGroups] Failed to register Productive Bees ingredient type: {}", e.getMessage());
		}
	}
}
