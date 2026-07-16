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
 * canonical IDs to viewer-specific ingredient type objects. Explicit registrations outrank
 * runtime-discovered IDs; discovery-owned entries can be discarded when the viewer generation
 * changes without disturbing public API or soft-dependency registrations.
 */
public final class IngredientTypeIds {
	private static final Set<String> CANONICAL_IDS = new LinkedHashSet<>();
	private static final Map<String, String> ALIASES = new LinkedHashMap<>();
	private static final Map<String, RegistrationOrigin> CANONICAL_ORIGINS = new LinkedHashMap<>();
	private static final Map<String, RegistrationOrigin> ALIAS_ORIGINS = new LinkedHashMap<>();

	public enum RegistrationOrigin {
		DISCOVERED,
		EXPLICIT;

		public boolean precedes(RegistrationOrigin other) {
			return ordinal() > other.ordinal();
		}
	}

	private IngredientTypeIds() {}

	public static synchronized void registerCanonical(String id) {
		registerCanonical(id, RegistrationOrigin.EXPLICIT);
	}

	public static synchronized void registerCanonical(String id, RegistrationOrigin origin) {
		requireNonReservedId(id);
		if (ALIASES.containsKey(id)) {
			throw new IllegalArgumentException("Canonical ID '" + id + "' is already registered as an alias.");
		}
		CANONICAL_IDS.add(id);
		CANONICAL_ORIGINS.merge(id, origin, (oldOrigin, newOrigin) ->
			newOrigin.precedes(oldOrigin) ? newOrigin : oldOrigin);
	}

	public static synchronized void registerAlias(String alias, String canonicalId) {
		registerAlias(alias, canonicalId, RegistrationOrigin.EXPLICIT);
	}

	public static synchronized void registerAlias(String alias, String canonicalId, RegistrationOrigin origin) {
		requireNonReservedId(alias);
		if (!CANONICAL_IDS.contains(canonicalId)) {
			throw new IllegalArgumentException(
				"Cannot alias '" + alias + "' to unknown canonical ID '" + canonicalId + "'. " +
					"Register the canonical type first."
			);
		}
		if (alias.equals(canonicalId)) return;
		if (CANONICAL_IDS.contains(alias)) {
			throw new IllegalArgumentException("Alias '" + alias + "' is already a canonical ID.");
		}
		String existing = ALIASES.get(alias);
		if (existing != null && !existing.equals(canonicalId)) {
			throw new IllegalArgumentException(
				"Alias '" + alias + "' already resolves to canonical ID '" + existing + "'."
			);
		}
		ALIASES.put(alias, canonicalId);
		ALIAS_ORIGINS.merge(alias, origin, (oldOrigin, newOrigin) ->
			newOrigin.precedes(oldOrigin) ? newOrigin : oldOrigin);
	}

	public static synchronized void replaceDiscoveredCanonical(String discoveredId, String explicitId) {
		if (CANONICAL_ORIGINS.get(discoveredId) != RegistrationOrigin.DISCOVERED) {
			throw new IllegalArgumentException("Canonical ID '" + discoveredId + "' is not discovery-owned.");
		}
		requireNonReservedId(explicitId);
		if (!discoveredId.equals(explicitId) && (CANONICAL_IDS.contains(explicitId) || ALIASES.containsKey(explicitId))) {
			throw new IllegalArgumentException("Explicit ID '" + explicitId + "' is already registered.");
		}
		CANONICAL_IDS.remove(discoveredId);
		CANONICAL_ORIGINS.remove(discoveredId);
		CANONICAL_IDS.add(explicitId);
		CANONICAL_ORIGINS.put(explicitId, RegistrationOrigin.EXPLICIT);
		for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
			if (entry.getValue().equals(discoveredId)) entry.setValue(explicitId);
		}
		if (!discoveredId.equals(explicitId)) {
			ALIASES.put(discoveredId, explicitId);
			ALIAS_ORIGINS.put(discoveredId, RegistrationOrigin.DISCOVERED);
		}
	}

	public static synchronized Set<String> clearDiscovered() {
		Set<String> removedCanonicalIds = new LinkedHashSet<>();
		CANONICAL_ORIGINS.forEach((id, origin) -> {
			if (origin == RegistrationOrigin.DISCOVERED) removedCanonicalIds.add(id);
		});
		ALIASES.entrySet().removeIf(entry ->
			ALIAS_ORIGINS.get(entry.getKey()) == RegistrationOrigin.DISCOVERED
				|| removedCanonicalIds.contains(entry.getValue()));
		ALIAS_ORIGINS.keySet().retainAll(ALIASES.keySet());
		CANONICAL_IDS.removeAll(removedCanonicalIds);
		removedCanonicalIds.forEach(CANONICAL_ORIGINS::remove);
		return Set.copyOf(removedCanonicalIds);
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

	@Nullable
	public static synchronized RegistrationOrigin getCanonicalOrigin(String canonicalId) {
		return CANONICAL_ORIGINS.get(canonicalId);
	}

	@Nullable
	public static synchronized RegistrationOrigin getAliasOrigin(String alias) {
		return ALIAS_ORIGINS.get(alias);
	}

	private static void requireNonReservedId(String id) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("Ingredient type IDs must not be null or blank.");
		}
		if ("item".equals(id) || "fluid".equals(id)) {
			throw new IllegalArgumentException("IDs 'item' and 'fluid' are reserved for built-in types.");
		}
	}
}
