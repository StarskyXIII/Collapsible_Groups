package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Discovers JEI ingredient types after JEI has constructed its ingredient manager. */
final class JeiIngredientTypeDiscovery {
	private static final Set<String> WARNED_DISCOVERY_PROBLEMS = ConcurrentHashMap.newKeySet();
	private static final Set<String> WARNED_UNRESOLVED_TYPES = ConcurrentHashMap.newKeySet();
	private static IIngredientManager currentManager;

	private JeiIngredientTypeDiscovery() {}

	static synchronized DiscoveryReport discover(IIngredientManager manager) {
		if (currentManager != manager) {
			JeiIngredientTypes.clearDiscovered();
			currentManager = manager;
		}
		int discovered = 0;
		int aliased = 0;
		int skipped = 0;
		for (IIngredientType<?> type : manager.getRegisteredIngredientTypes()) {
			if (isExcluded(type)) continue;
			String uid;
			try {
				uid = type.getUid();
			} catch (RuntimeException error) {
				warnDiscoveryOnce("uid-error:" + System.identityHashCode(type),
					"Skipping JEI ingredient type whose uid could not be read: " + error.getMessage());
				skipped++;
				continue;
			}
			if (uid == null || uid.isBlank() || "item".equals(uid) || "fluid".equals(uid)) {
				warnDiscoveryOnce("invalid:" + String.valueOf(uid) + ":" + System.identityHashCode(type),
					"Skipping JEI ingredient type with null, blank, or reserved uid '" + uid + "'.");
				skipped++;
				continue;
			}

			String existingForType = JeiIngredientTypes.getCanonicalId(type);
			String occupiedCanonical = IngredientTypeIds.getCanonicalId(uid);
			IIngredientType<?> occupiedType = JeiIngredientTypes.get(uid);
			if (occupiedCanonical != null && occupiedType != type) {
				warnDiscoveryOnce("collision:" + uid,
					"Skipping JEI ingredient type uid collision for '" + uid + "'; the existing registration wins.");
				skipped++;
				continue;
			}
			try {
				boolean newCanonical = JeiIngredientTypes.registerDiscovered(uid, type);
				if (newCanonical) discovered++;
				else if (existingForType != null && !uid.equals(existingForType)) aliased++;
			} catch (IllegalArgumentException error) {
				warnDiscoveryOnce("rejected:" + uid,
					"Skipping JEI ingredient type uid '" + uid + "': " + error.getMessage());
				skipped++;
			}
		}
		return new DiscoveryReport(discovered, aliased, skipped);
	}

	static synchronized void clearRuntimeTypes() {
		currentManager = null;
		JeiIngredientTypes.clearDiscovered();
	}

	static int warnUnresolvedTypesAfterBootstrap(List<GroupDefinition> groups) {
		int warnings = 0;
		for (String typeId : unresolvedTypeIds(groups)) {
			if (WARNED_UNRESOLVED_TYPES.add(typeId)) {
				Constants.LOG.warn("Ingredient type '{}' is unresolved after JEI bootstrap; filters using it will "
					+ "match nothing. The provider may have changed its JEI type uid.", typeId);
				warnings++;
			}
		}
		return warnings;
	}

	static Set<String> unresolvedTypeIds(List<GroupDefinition> groups) {
		Set<String> referenced = new LinkedHashSet<>();
		for (GroupDefinition group : groups) collectTypes(group.filter(), referenced);
		referenced.removeIf(type -> "item".equals(type) || "fluid".equals(type)
			|| IngredientTypeIds.getCanonicalId(type) != null);
		return Set.copyOf(referenced);
	}

	private static void collectTypes(GroupFilter filter, Set<String> output) {
		switch (filter) {
			case GroupFilter.Any any -> any.children().forEach(child -> collectTypes(child, output));
			case GroupFilter.All all -> all.children().forEach(child -> collectTypes(child, output));
			case GroupFilter.Not not -> collectTypes(not.child(), output);
			case GroupFilter.Id id -> output.add(id.ingredientType());
			case GroupFilter.Tag tag -> output.add(tag.ingredientType());
			case GroupFilter.Namespace namespace -> output.add(namespace.ingredientType());
			default -> { }
		}
	}

	private static boolean isExcluded(IIngredientType<?> type) {
		return type == VanillaTypes.ITEM_STACK
			|| type == JeiIngredientTypes.getFluidType()
			|| type == GroupIcon.TYPE;
	}

	private static void warnDiscoveryOnce(String key, String message) {
		if (WARNED_DISCOVERY_PROBLEMS.add(key)) Constants.LOG.warn(message);
	}

	record DiscoveryReport(int canonicalTypes, int aliases, int skipped) {}
}
