package com.starskyxiii.collapsible_groups.compat.jei.api;

import mezz.jei.api.ingredients.IIngredientType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry mapping string IDs to JEI IIngredientType instances for
 * custom ingredient types (anything beyond the built-in "item" and "fluid").
 *
 * Registrations must happen during mod initialization ??before KubeJS
 * loads scripts ??so that CollapsibleGroupsKubeJSPlugin can expose them
 * as RecipeViewerEntryType instances to the script layer.
 *
 * Aliases allow short names (e.g. "chemical") to resolve to a canonical
 * namespaced ID (e.g. "mekanism:chemical"). Both the canonical ID and any
 * aliases are exposed to KubeJS as valid entry type strings.
 *
 * Use {@link CGApi#registerIngredientType} and
 * {@link CGApi#registerIngredientTypeAlias} as the public entry points.
 *
 * <p>A {@link LinkedHashMap} is used intentionally so registration order stays deterministic
 * for iteration and debugging. Registration is expected to happen during startup before
 * runtime lookups begin.
 */
public final class IngredientTypeRegistry {
	private static final Map<String, IIngredientType<?>> REGISTRY = new LinkedHashMap<>();
	/** Maps alias -> canonical ID. */
	private static final Map<String, String> ALIASES = new LinkedHashMap<>();

	private IngredientTypeRegistry() {}

	/**
	 * Registers a custom ingredient type under the given string ID.
	 * The IDs "item" and "fluid" are reserved and will throw.
	 * Must be called during mod initialization.
	 */
	static void register(String id, IIngredientType<?> type) {
		if ("item".equals(id) || "fluid".equals(id)) {
			throw new IllegalArgumentException("IDs 'item' and 'fluid' are reserved for built-in types.");
		}
		REGISTRY.put(id, type);
	}

	/**
	 * Registers a short alias that resolves to an already-registered canonical ID.
	 * For example: registerAlias("chemical", "mekanism:chemical").
	 * The canonical ID must have been registered first via {@link #register}.
	 */
	static void registerAlias(String alias, String canonicalId) {
		if ("item".equals(alias) || "fluid".equals(alias)) {
			throw new IllegalArgumentException("IDs 'item' and 'fluid' are reserved for built-in types.");
		}
		if (!REGISTRY.containsKey(canonicalId)) {
			throw new IllegalArgumentException(
				"Cannot alias '" + alias + "' to unknown canonical ID '" + canonicalId + "'. " +
				"Register the canonical type first."
			);
		}
		ALIASES.put(alias, canonicalId);
	}

	/** Returns the IIngredientType for the given ID or alias, or null if not registered. */
	@Nullable
	public static IIngredientType<?> get(String id) {
		IIngredientType<?> direct = REGISTRY.get(id);
		if (direct != null) return direct;
		String canonical = ALIASES.get(id);
		return canonical != null ? REGISTRY.get(canonical) : null;
	}

	/**
	 * Returns the canonical ID for the given ID or alias, or null if it is unknown.
	 */
	@Nullable
	public static String getCanonicalId(String id) {
		if (REGISTRY.containsKey(id)) return id;
		return ALIASES.get(id);
	}

	/**
	 * Returns the first canonical ID registered for the given type instance.
	 */
	@Nullable
	public static String getCanonicalId(IIngredientType<?> type) {
		for (Map.Entry<String, IIngredientType<?>> entry : REGISTRY.entrySet()) {
			if (entry.getValue().equals(type)) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** Returns an unmodifiable view of canonical ID -> type (excludes aliases). */
	public static Map<String, IIngredientType<?>> getAll() {
		return Collections.unmodifiableMap(REGISTRY);
	}

	/**
	 * Returns an unmodifiable map of all IDs (canonical + aliases) ??type.
	 * Use this when every name a type is known by needs to be exposed or iterated,
	 * e.g. when registering RecipeViewerEntryType instances for KubeJS.
	 */
	public static Map<String, IIngredientType<?>> getAllWithAliases() {
		Map<String, IIngredientType<?>> all = new LinkedHashMap<>(REGISTRY);
		ALIASES.forEach((alias, canonicalId) -> {
			IIngredientType<?> type = REGISTRY.get(canonicalId);
			if (type != null) all.put(alias, type);
		});
		return Collections.unmodifiableMap(all);
	}
}
