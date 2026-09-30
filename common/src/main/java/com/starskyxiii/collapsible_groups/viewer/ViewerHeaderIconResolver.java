package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves configured header icons against a viewer universe in stable display order. */
public final class ViewerHeaderIconResolver {
	private static final int MAX_ICONS = 2;

	private ViewerHeaderIconResolver() {}

	public static <E> List<ViewerIngredient<E>> resolve(
		List<GroupIconDefinition> configured,
		List<ViewerIngredient<E>> fallback,
		ViewerIngredientUniverse<E> universe
	) {
		List<ViewerIngredient<E>> result = new ArrayList<>(MAX_ICONS);
		Set<ViewerIngredientIdentity> seen = new LinkedHashSet<>();
		for (GroupIconDefinition icon : configured) {
			ViewerIngredient<E> resolved = find(icon, universe);
			if (resolved != null && seen.add(resolved.identity())) result.add(resolved);
			if (result.size() == MAX_ICONS) return List.copyOf(result);
		}
		for (ViewerIngredient<E> candidate : fallback) {
			if (seen.add(candidate.identity())) result.add(candidate);
			if (result.size() == MAX_ICONS) break;
		}
		return List.copyOf(result);
	}

	public static <E> ViewerIngredient<E> find(
		GroupIconDefinition icon,
		ViewerIngredientUniverse<E> universe
	) {
		return universe.findIcon(icon.canonicalIngredientType(), icon.valueId());
	}

	public static <E> List<ViewerIngredient<E>> resolveDefinitions(
		List<GroupIconDefinition> configured, Iterable<GroupIconDefinition> fallback,
		ViewerIngredientUniverse<E> universe
	) {
		List<ViewerIngredient<E>> result = new ArrayList<>(MAX_ICONS);
		Set<ViewerIngredientIdentity> seen = new LinkedHashSet<>();
		for (var definitions : List.of(configured, fallback)) {
			for (GroupIconDefinition icon : definitions) {
				ViewerIngredient<E> resolved = find(icon, universe);
				if (resolved != null && seen.add(resolved.identity())) result.add(resolved);
				if (result.size() == MAX_ICONS) return List.copyOf(result);
			}
		}
		return List.copyOf(result);
	}
}
