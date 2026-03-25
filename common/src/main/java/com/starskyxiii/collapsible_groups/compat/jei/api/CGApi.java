package com.starskyxiii.collapsible_groups.compat.jei.api;

import mezz.jei.api.ingredients.IIngredientType;

/**
 * Public API for Collapsible Groups.
 *
 * Third-party mods can call {@link #registerIngredientType} during their own
 * mod initialization to expose a custom JEI ingredient type (e.g. a mod's Gas
 * or Bee type) to KubeJS scripts via RecipeViewerEvents.groupEntries().
 *
 * <p>Example usage from another mod:
 * <pre>{@code
 * // In your mod's constructor or FMLClientSetupEvent handler:
 * CGApi.registerIngredientType("mymod:mytype", MyJeiPlugin.MY_INGREDIENT_TYPE);
 * }</pre>
 *
 * Scripts can then group these ingredients:
 * <pre>{@code
 * RecipeViewerEvents.groupEntries('mymod:mytype', event => {
 *     event.group('@mymod', 'mypack:all_mymod_things', 'All MyMod Things')
 * })
 * }</pre>
 */
public final class CGApi {
	private CGApi() {}

	/**
	 * Registers a custom JEI ingredient type under the given string ID.
	 *
	 * <p>Must be called during mod initialization (before KubeJS loads scripts).
	 * The IDs {@code "item"} and {@code "fluid"} are reserved.
	 *
	 * @param id   A namespaced string ID, e.g. {@code "mekanism:chemical"}
	 * @param type The JEI IIngredientType to associate with this ID
	 */
	public static void registerIngredientType(String id, IIngredientType<?> type) {
		IngredientTypeRegistry.register(id, type);
	}

	/**
	 * Registers a short alias for an already-registered canonical type ID.
	 * Both the canonical ID and the alias will be valid entry type strings
	 * in KubeJS scripts.
	 *
	 * <p>Example: after calling
	 * {@code CGApi.registerIngredientTypeAlias("chemical", "mekanism:chemical")},
	 * scripts may use either {@code 'chemical'} or {@code 'mekanism:chemical'}.
	 *
	 * @param alias      The short alias, e.g. {@code "chemical"}
	 * @param canonicalId The canonical ID registered via {@link #registerIngredientType}
	 */
	public static void registerIngredientTypeAlias(String alias, String canonicalId) {
		IngredientTypeRegistry.registerAlias(alias, canonicalId);
	}
}
