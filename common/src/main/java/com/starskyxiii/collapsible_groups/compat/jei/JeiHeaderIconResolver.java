package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves persistent icon identities against JEI's already-created ingredient universe. */
final class JeiHeaderIconResolver {
	private static final Set<String> WARNED_UNRESOLVED = new HashSet<>();
	private static ViewerIngredientUniverse<ITypedIngredient<?>> indexedUniverse;
	private static Map<IconKey, ITypedIngredient<?>> indexedIcons = Map.of();

	private JeiHeaderIconResolver() {}

	static List<ITypedIngredient<?>> assemble(
		List<GroupIconDefinition> iconIds,
		List<ViewerIngredient<ITypedIngredient<?>>> fallback,
		ViewerIngredientUniverse<ITypedIngredient<?>> universe
	) {
		List<ITypedIngredient<?>> explicit = resolve(iconIds, universe);
		if (!explicit.isEmpty()) return explicit;
		return fallback.stream().limit(2).map(ViewerIngredient::entry).toList();
	}

	static List<ITypedIngredient<?>> resolve(
		List<GroupIconDefinition> iconIds,
		ViewerIngredientUniverse<ITypedIngredient<?>> universe
	) {
		List<ITypedIngredient<?>> result = new ArrayList<>(Math.min(2, iconIds.size()));
		for (GroupIconDefinition icon : iconIds) {
			find(icon, universe).ifPresent(result::add);
			if (result.size() == 2) break;
		}
		return List.copyOf(result);
	}

	static synchronized int warnUnresolvedAfterBootstrap(
		List<GroupDefinition> groups,
		ViewerIngredientUniverse<ITypedIngredient<?>> universe
	) {
		int warnings = 0;
		for (GroupDefinition group : groups) {
			for (GroupIconDefinition icon : group.iconIds()) {
				if (find(icon, universe).isPresent()) continue;
				String key = group.id() + '|' + icon.ingredientType() + '|' + icon.valueId();
				if (!WARNED_UNRESOLVED.add(key)) continue;
				warnings++;
				Constants.LOG.warn("Group '{}': icon type '{}' id '{}' is unavailable after JEI bootstrap; "
					+ "the saved value is unchanged and child icons will be used when no explicit icon resolves.",
					group.id(), icon.ingredientType(), icon.valueId());
			}
		}
		return warnings;
	}

	static synchronized void clearWarnings() {
		WARNED_UNRESOLVED.clear();
		indexedUniverse = null;
		indexedIcons = Map.of();
	}

	private static java.util.Optional<ITypedIngredient<?>> find(
		GroupIconDefinition icon,
		ViewerIngredientUniverse<ITypedIngredient<?>> universe
	) {
		return java.util.Optional.ofNullable(index(universe).get(
			new IconKey(icon.canonicalIngredientType(), icon.valueId())));
	}

	private static synchronized Map<IconKey, ITypedIngredient<?>> index(
		ViewerIngredientUniverse<ITypedIngredient<?>> universe
	) {
		if (indexedUniverse == universe) return indexedIcons;
		Map<IconKey, ITypedIngredient<?>> result = new LinkedHashMap<>();
		for (ViewerIngredient<ITypedIngredient<?>> candidate : universe.ordered()) {
			String candidateType = IngredientTypeIds.getCanonicalId(candidate.identity().typeId());
			if (candidateType == null) candidateType = candidate.identity().typeId();
			result.putIfAbsent(new IconKey(candidateType, candidate.identity().valueId()), candidate.entry());
			if (("item".equals(candidateType) || "fluid".equals(candidateType))
				&& candidate.view().resourceLocation() != null
			) {
				result.putIfAbsent(new IconKey(candidateType,
					candidate.view().resourceLocation().toString()), candidate.entry());
			}
		}
		indexedUniverse = universe;
		indexedIcons = Map.copyOf(result);
		return indexedIcons;
	}

	private record IconKey(String typeId, String valueId) {}
}
