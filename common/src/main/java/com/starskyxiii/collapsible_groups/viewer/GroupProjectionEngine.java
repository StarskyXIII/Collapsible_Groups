package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupCatalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Computes viewer-independent ownership and collapsible group projection. */
public final class GroupProjectionEngine {
	private GroupProjectionEngine() {}

	public static <E> ViewerProjection<E> project(
		ViewerIngredientUniverse<E> universe,
		ViewerSearchSnapshot<E> search,
		List<GroupDefinition> groups,
		GroupExpansionState expansionState
	) {
		GroupCandidateIndex candidates = buildCandidateIndex(universe, groups);
		return project(universe, search, groups, expansionState, candidates);
	}

	public static <E> ViewerProjection<E> project(
		ViewerIngredientUniverse<E> universe,
		ViewerSearchSnapshot<E> search,
		List<GroupDefinition> groups,
		GroupExpansionState expansionState,
		GroupCandidateIndex candidates
	) {
		return project(universe, search, groups, expansionState, resolveOwnership(candidates, groups));
	}

	public static <E> ViewerProjection<E> project(
		ViewerIngredientUniverse<E> universe,
		ViewerSearchSnapshot<E> search,
		List<GroupDefinition> groups,
		GroupExpansionState expansionState,
		Map<ViewerIngredientIdentity, String> ownership
	) {
		Map<String, GroupDefinition> groupsById = new LinkedHashMap<>();
		for (GroupDefinition group : groups) groupsById.put(group.id(), group);
		Map<ViewerIngredientIdentity, GroupDefinition> owners = new LinkedHashMap<>();
		ownership.forEach((identity, groupId) -> {
			GroupDefinition group = groupsById.get(groupId);
			if (group != null && group.enabled()) owners.put(identity, group);
		});

		Map<String, GroupDefinition> ownedGroups = new LinkedHashMap<>();
		Map<String, List<ViewerIngredient<E>>> items = new LinkedHashMap<>();
		Map<String, List<ViewerIngredient<E>>> fluids = new LinkedHashMap<>();
		Map<String, List<ViewerIngredient<E>>> generic = new LinkedHashMap<>();
		for (ViewerIngredient<E> ingredient : search.filteredResults()) {
			GroupDefinition owner = owners.get(ingredient.identity());
			if (owner == null) continue;
			ownedGroups.putIfAbsent(owner.id(), owner);
			Map<String, List<ViewerIngredient<E>>> bucket = switch (ingredient.kind()) {
				case ITEM -> items;
				case FLUID -> fluids;
				case GENERIC -> generic;
			};
			bucket.computeIfAbsent(owner.id(), ignored -> new ArrayList<>()).add(ingredient);
		}

		List<ViewerProjection.Entry<E>> projected = new ArrayList<>();
		Set<String> emittedHeaders = new HashSet<>();
		for (ViewerIngredient<E> ingredient : search.filteredResults()) {
			GroupDefinition owner = owners.get(ingredient.identity());
			if (owner != null) {
				List<ViewerIngredient<E>> itemChildren = items.getOrDefault(owner.id(), List.of());
				List<ViewerIngredient<E>> fluidChildren = fluids.getOrDefault(owner.id(), List.of());
				List<ViewerIngredient<E>> genericChildren = generic.getOrDefault(owner.id(), List.of());
				int childCount = itemChildren.size() + fluidChildren.size() + genericChildren.size();
				if (childCount >= 2) {
					if (shouldUngroupForSearch(search, childCount)) {
						projected.add(new ViewerProjection.IngredientEntry<>(ingredient));
						continue;
					}
					if (emittedHeaders.add(owner.id())) {
						List<ViewerIngredient<E>> children = new ArrayList<>(childCount);
						children.addAll(itemChildren);
						children.addAll(fluidChildren);
						children.addAll(genericChildren);
						projected.add(new ViewerProjection.GroupHeader<>(
							ownedGroups.get(owner.id()),
							children,
							itemChildren.size(),
							fluidChildren.size(),
							genericChildren.size(),
							expansionState.isExpanded(owner.id()),
							owner.iconIds(),
							children.subList(0, Math.min(2, children.size()))
						));
					}
					continue;
				}
			}
			projected.add(new ViewerProjection.IngredientEntry<>(ingredient));
		}

		return new ViewerProjection<>(projected, ownership);
	}

	public static <E> Map<ViewerIngredientIdentity, String> buildOwnership(
		ViewerIngredientUniverse<E> universe,
		List<GroupDefinition> groups
	) {
		return resolveOwnership(buildCandidateIndex(universe, groups), groups);
	}

	public static <E> GroupCandidateIndex buildCandidateIndex(
		ViewerIngredientUniverse<E> universe,
		List<GroupDefinition> groups
	) {
		List<GroupDefinition> priorityOrder = GroupCatalog.orderByPriority(groups);
		List<GroupDefinition> itemGroups = priorityOrder.stream().filter(GroupDefinition::hasItemFilters).toList();
		List<GroupDefinition> fluidGroups = priorityOrder.stream().filter(GroupDefinition::hasFluidFilters).toList();
		List<GroupDefinition> genericGroups = priorityOrder.stream().filter(GroupDefinition::hasGenericFilters).toList();
		Map<ViewerIngredientIdentity, List<String>> candidates = new LinkedHashMap<>();
		Map<String, GroupDefinition> snapshot = new LinkedHashMap<>();
		priorityOrder.forEach(group -> snapshot.put(group.id(), group));
		long edges = 0;
		int max = 0;
		for (ViewerIngredient<E> ingredient : universe.ordered()) {
			List<GroupDefinition> applicable = switch (ingredient.kind()) {
				case ITEM -> itemGroups;
				case FLUID -> fluidGroups;
				case GENERIC -> genericGroups;
			};
			List<String> matches = new ArrayList<>();
			for (GroupDefinition group : applicable) {
				if (group.compiledFilter().matches(ingredient.view())) matches.add(group.id());
			}
			if (!matches.isEmpty()) candidates.put(ingredient.identity(), List.copyOf(matches));
			edges += matches.size();
			max = Math.max(max, matches.size());
		}
		return new GroupCandidateIndex(candidates, snapshot, edges, universe.ordered().size(), max);
	}

	public static Map<ViewerIngredientIdentity, String> resolveOwnership(
		GroupCandidateIndex candidates,
		List<GroupDefinition> groups
	) {
		Map<String, GroupDefinition> current = new LinkedHashMap<>();
		for (GroupDefinition group : groups) current.put(group.id(), group);
		Map<ViewerIngredientIdentity, String> result = new LinkedHashMap<>();
		candidates.candidates().forEach((identity, groupIds) -> {
			for (String groupId : groupIds) {
				GroupDefinition group = current.get(groupId);
				if (group != null && group.enabled()) {
					result.put(identity, groupId);
					break;
				}
			}
		});
		return Map.copyOf(result);
	}

	private static boolean shouldUngroupForSearch(ViewerSearchSnapshot<?> search, int childCount) {
		return search.ungroupSmallGroups()
			&& search.searchActive()
			&& search.ungroupThreshold() > 0
			&& childCount >= 2
			&& childCount < search.ungroupThreshold();
	}
}
