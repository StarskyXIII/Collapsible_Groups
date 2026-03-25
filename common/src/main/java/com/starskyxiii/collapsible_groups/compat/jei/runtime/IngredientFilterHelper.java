package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared helpers for the IngredientFilter mixin across all loaders.
 *
 * <p>Only contains logic that does not depend on {@code IElement} or other
 * GUI classes, because those are only available in loader-specific JEI artifacts.
 *
 * <p>Build results are written directly into {@link GroupRegistry}'s static caches via {@link #publish}.
 */
public final class IngredientFilterHelper {
	private static final String STARTUP_INDEX_VERIFY_OVERRIDE =
		System.getProperty("collapsible_groups.startup_index_verify");

	private IngredientFilterHelper() {}

	/**
	 * Scans {@code all} JEI ingredients and maps each item ingredient to its
	 * {@link GroupDefinition} (first match, ignoring enabled state).
	 *
	 * <p>Also updates {@link GroupRegistry#setResolvedItemsByGroup} so the
	 * editor/manager can do O(1) group-member lookups.
	 *
	 * <p>The returned map is an {@link IdentityHashMap} and is mutable so
	 * NeoForge can add fluid/generic entries after calling this method.
	 *
	 * <p>For Fabric/Forge (item-only) this covers the full index. For NeoForge,
	 * callers should add fluid and generic entries on top of the returned map.
	 */
	public static Map<ITypedIngredient<?>, GroupDefinition> buildItemGroupIndex(
		List<ITypedIngredient<?>> all
	) {
		boolean startupIndexVerifyEnabled = isStartupIndexVerifyEnabled();
		long traceStart = PerformanceTrace.begin();
		List<GroupDefinition> allGroups = GroupRegistry.getAllIncludingKubeJs();
		long itemGroups = allGroups.stream().filter(GroupDefinition::hasItemFilters).count();

		IngredientFilterItemIndex itemIndex = IngredientFilterItemIndex.build(all);
		int itemIngredients = itemIndex.orderedEntries().size();
		ItemOwnershipBuildResult optimizedResult = buildOptimizedItemOwnershipResult(itemIndex, allGroups);
		String mode = "optimized";

		if (startupIndexVerifyEnabled) {
			ItemOwnershipBuildResult referenceResult =
				buildReferenceItemOwnershipResult(itemIndex.orderedEntries(), allGroups);
			String verificationMismatch = verifyItemOwnershipResults(referenceResult, optimizedResult);
			if (verificationMismatch != null) {
				Constants.LOG.error(
					"[CollapsibleGroups] Startup index verification failed: {}. Continuing with the optimized startup index result.",
					verificationMismatch
				);
				mode = "optimized_verify_mismatch";
			} else {
				mode = "optimized_verified";
			}
		}

		publish(optimizedResult);
		PerformanceTrace.logIfSlow("IngredientFilterHelper.buildItemGroupIndex", traceStart, 20,
			"mode=" + mode
				+ " ingredients=" + all.size()
				+ " itemIngredients=" + itemIngredients
				+ " itemGroups=" + itemGroups
				+ " resolvedGroups=" + optimizedResult.resolvedEntriesByGroup().size()
				+ " fullMatchGroups=" + optimizedResult.fullMatchEntriesByGroup().size()
				+ " itemIds=" + optimizedResult.itemIdToGroupIds().size()
				+ " indexSize=" + optimizedResult.ingredientGroupIndex().size());
		return optimizedResult.ingredientGroupIndex();
	}

	private static ItemOwnershipBuildResult buildReferenceItemOwnershipResult(
		List<IngredientFilterItemIndex.ItemEntry> orderedEntries,
		List<GroupDefinition> allGroups
	) {
		IdentityHashMap<ITypedIngredient<?>, GroupDefinition> ingredientGroupIndex =
			new IdentityHashMap<>(Math.max(16, orderedEntries.size() * 2));
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> fullMatchEntriesByGroup = newEntryMap(allGroups);
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> resolvedEntriesByGroup = newEntryMap(allGroups);
		List<GroupDefinition> itemGroups = allGroups.stream()
			.filter(GroupDefinition::hasItemFilters)
			.toList();

		for (IngredientFilterItemIndex.ItemEntry entry : orderedEntries) {
			GroupDefinition firstMatch = null;
			for (GroupDefinition group : itemGroups) {
				if (!group.matchesIgnoringEnabled(entry.stack())) continue;
				if (firstMatch == null) {
					firstMatch = group;
					ingredientGroupIndex.put(entry.typed(), group);
					resolvedEntriesByGroup.get(group.id()).add(entry);
				}
				fullMatchEntriesByGroup.get(group.id()).add(entry);
			}
		}

		return finalizeBuildResult(ingredientGroupIndex, fullMatchEntriesByGroup, resolvedEntriesByGroup);
	}

	private static ItemOwnershipBuildResult buildOptimizedItemOwnershipResult(
		IngredientFilterItemIndex itemIndex,
		List<GroupDefinition> allGroups
	) {
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> fullMatchEntriesByGroup = newEntryMap(allGroups);
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> resolvedEntriesByGroup = newEntryMap(allGroups);

		for (GroupDefinition group : allGroups) {
			if (!group.hasItemFilters()) {
				continue;
			}

			List<IngredientFilterItemIndex.ItemEntry> domain = switch (ItemFilterQueryCompiler.compile(group.filter())) {
				case ItemFilterQueryCompiler.EmptyPlan ignored -> List.of();
				case ItemFilterQueryCompiler.AllItemsPlan ignored -> itemIndex.orderedEntries();
				case ItemFilterQueryCompiler.CandidatePlan candidate -> candidate.collectCandidates(itemIndex);
				case ItemFilterQueryCompiler.FullScanPlan ignored -> itemIndex.orderedEntries();
			};

			if (domain.isEmpty()) {
				continue;
			}

			List<IngredientFilterItemIndex.ItemEntry> verified = fullMatchEntriesByGroup.get(group.id());
			for (IngredientFilterItemIndex.ItemEntry entry : domain) {
				if (group.matchesIgnoringEnabled(entry.stack())) {
					verified.add(entry);
				}
			}
		}

		IdentityHashMap<ITypedIngredient<?>, GroupDefinition> ingredientGroupIndex =
			new IdentityHashMap<>(Math.max(16, itemIndex.orderedEntries().size() * 2));
		IdentityHashMap<ITypedIngredient<?>, Boolean> owned =
			new IdentityHashMap<>(Math.max(16, itemIndex.orderedEntries().size() * 2));

		for (GroupDefinition group : allGroups) {
			List<IngredientFilterItemIndex.ItemEntry> fullMatchEntries = fullMatchEntriesByGroup.get(group.id());
			List<IngredientFilterItemIndex.ItemEntry> resolvedEntries = resolvedEntriesByGroup.get(group.id());
			for (IngredientFilterItemIndex.ItemEntry entry : fullMatchEntries) {
				if (owned.put(entry.typed(), Boolean.TRUE) != null) continue;
				ingredientGroupIndex.put(entry.typed(), group);
				resolvedEntries.add(entry);
			}
		}

		return finalizeBuildResult(ingredientGroupIndex, fullMatchEntriesByGroup, resolvedEntriesByGroup);
	}

	private static ItemOwnershipBuildResult finalizeBuildResult(
		IdentityHashMap<ITypedIngredient<?>, GroupDefinition> ingredientGroupIndex,
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> fullMatchEntriesByGroup,
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> resolvedEntriesByGroup
	) {
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> frozenFullMatchEntriesByGroup =
			freezeEntryMap(fullMatchEntriesByGroup);
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> frozenResolvedEntriesByGroup =
			freezeEntryMap(resolvedEntriesByGroup);
		return new ItemOwnershipBuildResult(
			ingredientGroupIndex,
			frozenFullMatchEntriesByGroup,
			frozenResolvedEntriesByGroup,
			buildItemIdToGroupIds(frozenResolvedEntriesByGroup)
		);
	}

	private static void publish(ItemOwnershipBuildResult result) {
		GroupRegistry.setResolvedItemsByGroup(toStackMap(result.resolvedEntriesByGroup()));
		GroupRegistry.setFullMatchItemsByGroup(toStackMap(result.fullMatchEntriesByGroup()));
		GroupRegistry.setItemIdToGroupIds(result.itemIdToGroupIds());
	}

	private static Map<String, List<IngredientFilterItemIndex.ItemEntry>> newEntryMap(List<GroupDefinition> allGroups) {
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> map = new LinkedHashMap<>(Math.max(16, allGroups.size() * 2));
		for (GroupDefinition group : allGroups) {
			map.put(group.id(), new ArrayList<>());
		}
		return map;
	}

	private static Map<String, List<IngredientFilterItemIndex.ItemEntry>> freezeEntryMap(
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> mutable
	) {
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> copy = new LinkedHashMap<>(mutable.size());
		for (var entry : mutable.entrySet()) {
			copy.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return Map.copyOf(copy);
	}

	private static Map<String, List<ItemStack>> toStackMap(
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> entriesByGroup
	) {
		Map<String, List<ItemStack>> stacksByGroup = new HashMap<>(Math.max(16, entriesByGroup.size() * 2));
		for (var entry : entriesByGroup.entrySet()) {
			List<ItemStack> stacks = new ArrayList<>(entry.getValue().size());
			for (IngredientFilterItemIndex.ItemEntry itemEntry : entry.getValue()) {
				stacks.add(itemEntry.stack());
			}
			stacksByGroup.put(entry.getKey(), List.copyOf(stacks));
		}
		return stacksByGroup;
	}

	private static Map<String, Set<String>> buildItemIdToGroupIds(
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> resolvedEntriesByGroup
	) {
		Map<String, Set<String>> itemIdToGroupIds = new LinkedHashMap<>();
		for (var entry : resolvedEntriesByGroup.entrySet()) {
			String groupId = entry.getKey();
			for (IngredientFilterItemIndex.ItemEntry itemEntry : entry.getValue()) {
				String registryId = BuiltInRegistries.ITEM.getKey(itemEntry.stack().getItem()).toString();
				itemIdToGroupIds.computeIfAbsent(registryId, ignored -> new LinkedHashSet<>()).add(groupId);
			}
		}
		Map<String, Set<String>> frozen = new LinkedHashMap<>(itemIdToGroupIds.size());
		for (var entry : itemIdToGroupIds.entrySet()) {
			frozen.put(entry.getKey(), Set.copyOf(entry.getValue()));
		}
		return Map.copyOf(frozen);
	}

	private static @Nullable String verifyItemOwnershipResults(
		ItemOwnershipBuildResult referenceResult,
		ItemOwnershipBuildResult optimizedResult
	) {
		String mismatch = compareIngredientGroupIndex(
			referenceResult.ingredientGroupIndex(),
			optimizedResult.ingredientGroupIndex()
		);
		if (mismatch != null) return mismatch;

		mismatch = compareEntryMap(
			"fullMatchEntriesByGroup",
			referenceResult.fullMatchEntriesByGroup(),
			optimizedResult.fullMatchEntriesByGroup()
		);
		if (mismatch != null) return mismatch;

		mismatch = compareEntryMap(
			"resolvedEntriesByGroup",
			referenceResult.resolvedEntriesByGroup(),
			optimizedResult.resolvedEntriesByGroup()
		);
		if (mismatch != null) return mismatch;

		if (!referenceResult.itemIdToGroupIds().equals(optimizedResult.itemIdToGroupIds())) {
			return "itemIdToGroupIds mismatch";
		}
		return null;
	}

	private static @Nullable String compareIngredientGroupIndex(
		Map<ITypedIngredient<?>, GroupDefinition> expected,
		Map<ITypedIngredient<?>, GroupDefinition> actual
	) {
		if (expected.size() != actual.size()) {
			return "ingredientGroupIndex size mismatch expected=" + expected.size() + " actual=" + actual.size();
		}
		for (var entry : expected.entrySet()) {
			ITypedIngredient<?> ingredient = entry.getKey();
			GroupDefinition actualGroup = actual.get(ingredient);
			if (actualGroup == null && !actual.containsKey(ingredient)) {
				return "ingredientGroupIndex missing ingredient " + System.identityHashCode(ingredient);
			}
			if (!Objects.equals(entry.getValue(), actualGroup)) {
				return "ingredientGroupIndex group mismatch for ingredient "
					+ System.identityHashCode(ingredient)
					+ " expected=" + entry.getValue().id()
					+ " actual=" + (actualGroup == null ? "<null>" : actualGroup.id());
			}
		}
		for (ITypedIngredient<?> ingredient : actual.keySet()) {
			if (!expected.containsKey(ingredient)) {
				return "ingredientGroupIndex unexpected ingredient " + System.identityHashCode(ingredient);
			}
		}
		return null;
	}

	private static @Nullable String compareEntryMap(
		String label,
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> expected,
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> actual
	) {
		if (!expected.keySet().equals(actual.keySet())) {
			return label + " key mismatch";
		}
		for (String groupId : expected.keySet()) {
			List<IngredientFilterItemIndex.ItemEntry> expectedEntries = expected.get(groupId);
			List<IngredientFilterItemIndex.ItemEntry> actualEntries = actual.get(groupId);
			if (expectedEntries.size() != actualEntries.size()) {
				return label + " size mismatch for group=" + groupId
					+ " expected=" + expectedEntries.size()
					+ " actual=" + actualEntries.size();
			}
			for (int i = 0; i < expectedEntries.size(); i++) {
				IngredientFilterItemIndex.ItemEntry expectedEntry = expectedEntries.get(i);
				IngredientFilterItemIndex.ItemEntry actualEntry = actualEntries.get(i);
				if (expectedEntry.typed() != actualEntry.typed()) {
					return label + " identity mismatch for group=" + groupId + " index=" + i
						+ " expectedIdentity=" + System.identityHashCode(expectedEntry.typed())
						+ " actualIdentity=" + System.identityHashCode(actualEntry.typed());
				}
			}
		}
		return null;
	}

	private static boolean isStartupIndexVerifyEnabled() {
		if (STARTUP_INDEX_VERIFY_OVERRIDE != null) {
			return Boolean.parseBoolean(STARTUP_INDEX_VERIFY_OVERRIDE);
		}
		return Services.CONFIG.debugStartupIndexVerificationEnabled();
	}
}
