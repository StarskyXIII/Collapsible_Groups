package com.starskyxiii.collapsible_groups.ingredient;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry of canonical ingredient type IDs and aliases.
 *
 * <p>This registry stores string identities only. Viewer adapters own the mapping from
 * canonical IDs to viewer-specific ingredient type objects.
 */
public final class IngredientTypeIds {
	private static final Set<String> CANONICAL_IDS = new LinkedHashSet<>();
	private static final Map<String, String> ALIASES = new LinkedHashMap<>();

	private IngredientTypeIds() {}

	public static synchronized void registerCanonical(String id) {
		requireNonReservedId(id);
		CANONICAL_IDS.add(id);
	}

	public static synchronized void registerAlias(String alias, String canonicalId) {
		requireNonReservedId(alias);
		if (!CANONICAL_IDS.contains(canonicalId)) {
			throw new IllegalArgumentException(
				"Cannot alias '" + alias + "' to unknown canonical ID '" + canonicalId + "'. " +
					"Register the canonical type first."
			);
		}
		ALIASES.put(alias, canonicalId);
	}

	@Nullable
	public static synchronized String getCanonicalId(String id) {
		if (CANONICAL_IDS.contains(id)) return id;
		return ALIASES.get(id);
	}

	public static synchronized Set<String> getCanonicalIds() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(CANONICAL_IDS));
	}

	public static synchronized Map<String, String> getAliases() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(ALIASES));
	}

	public static synchronized Map<String, String> getAllIds() {
		Map<String, String> all = new LinkedHashMap<>();
		CANONICAL_IDS.forEach(id -> all.put(id, id));
		all.putAll(ALIASES);
		return Collections.unmodifiableMap(all);
	}

	private static void requireNonReservedId(String id) {
		if ("item".equals(id) || "fluid".equals(id)) {
			throw new IllegalArgumentException("IDs 'item' and 'fluid' are reserved for built-in types.");
		}
	}
}
