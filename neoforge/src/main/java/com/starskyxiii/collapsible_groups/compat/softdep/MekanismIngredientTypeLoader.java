package com.starskyxiii.collapsible_groups.compat.softdep;

import com.starskyxiii.collapsible_groups.compat.jei.api.CGApi;
import com.starskyxiii.collapsible_groups.Constants;
import mezz.jei.api.ingredients.IIngredientType;

/**
 * Registers Mekanism's ChemicalStack ingredient type under "mekanism:chemical".
 *
 * Uses reflection to avoid a compile-time dependency on Mekanism.
 * Called only when ModList confirms Mekanism is loaded.
 */
public final class MekanismIngredientTypeLoader {

	private MekanismIngredientTypeLoader() {}

	public static void register() {
		try {
			Class<?> jeiClass = Class.forName("mekanism.client.recipe_viewer.jei.MekanismJEI");
			@SuppressWarnings("unchecked")
			IIngredientType<?> type = (IIngredientType<?>) jeiClass.getField("TYPE_CHEMICAL").get(null);
			CGApi.registerIngredientType("mekanism:chemical", type);
			CGApi.registerIngredientTypeAlias("chemical", "mekanism:chemical");
			Constants.LOG.info("[CollapsibleGroups] Registered Mekanism chemical ingredient type.");
		} catch (ClassNotFoundException e) {
			// Mekanism JEI plugin class not present - safe to ignore
		} catch (Exception e) {
			Constants.LOG.warn("[CollapsibleGroups] Failed to register Mekanism ingredient type: {}", e.getMessage());
		}
	}
}
