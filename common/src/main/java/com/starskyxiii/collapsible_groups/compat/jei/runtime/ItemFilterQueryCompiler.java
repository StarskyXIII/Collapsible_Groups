package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.FilterTypeScope;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterNormalizer;
import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.internal.query.CompiledGroupQuery;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles a normalized runtime filter tree into an item-view query plan.
 *
 * <p>The plan is used only for candidate-domain reduction. Final membership still
 * verifies against the existing compiled filter via {@code group.matchesIgnoringEnabled}.
 */
public final class ItemFilterQueryCompiler {
	private static final EmptyPlan EMPTY = new EmptyPlan();
	private static final AllItemsPlan ALL_ITEMS = new AllItemsPlan();
	private static final FullScanPlan FULL_SCAN = new FullScanPlan();

	private ItemFilterQueryCompiler() {}

	public static ItemQueryPlan compile(GroupFilter filter) {
		return compileNormalized(GroupFilterNormalizer.normalize(filter));
	}

	public static ItemQueryPlan compile(CompiledGroupQuery query) {
		return compileNormalized(query.source());
	}

	private static ItemQueryPlan compileNormalized(GroupFilter filter) {
		if (filter instanceof GroupFilter.Any any) return compileAny(any.children());
		if (filter instanceof GroupFilter.All all) return compileAll(all.children());
		if (filter instanceof GroupFilter.Not not) return compileNot(not.child());
		if (filter instanceof GroupFilter.Id id) return compileId(id);
		if (filter instanceof GroupFilter.Tag tag) return compileTag(tag);
		if (filter instanceof GroupFilter.BlockTag blockTag) return compileBlockTag(blockTag);
		if (filter instanceof GroupFilter.Namespace namespace) return compileNamespace(namespace);
		if (filter instanceof GroupFilter.ExactStack exactStack) return compileExactStack(exactStack);
		return FULL_SCAN;
	}

	private static ItemQueryPlan compileAny(List<GroupFilter> children) {
		List<CandidatePlan> candidates = new ArrayList<>();
		for (GroupFilter child : children) {
			ItemQueryPlan plan = compileNormalized(child);
			if (plan instanceof AllItemsPlan) return ALL_ITEMS;
			if (plan instanceof CandidatePlan candidate) candidates.add(candidate);
			else if (plan instanceof FullScanPlan) return FULL_SCAN;
		}
		if (candidates.isEmpty()) return EMPTY;
		if (candidates.size() == 1) return candidates.get(0);
		List<CandidatePlan> stableCandidates = List.copyOf(candidates);
		return new CandidatePlan(index -> {
			List<List<IngredientFilterItemIndex.ItemEntry>> buckets = new ArrayList<>(stableCandidates.size());
			for (CandidatePlan candidate : stableCandidates) {
				buckets.add(candidate.collectCandidates(index));
			}
			return IngredientFilterItemIndex.collectOrderedUnion(buckets);
		});
	}

	private static ItemQueryPlan compileAll(List<GroupFilter> children) {
		List<CandidatePlan> candidates = new ArrayList<>();
		boolean sawNonAllItems = false;
		for (GroupFilter child : children) {
			ItemQueryPlan plan = compileNormalized(child);
			if (plan instanceof EmptyPlan) return EMPTY;
			if (plan instanceof CandidatePlan candidate) {
				sawNonAllItems = true;
				candidates.add(candidate);
			} else if (plan instanceof FullScanPlan) {
				sawNonAllItems = true;
			}
		}
		if (!sawNonAllItems) return ALL_ITEMS;
		if (candidates.isEmpty()) return FULL_SCAN;
		if (candidates.size() == 1) return candidates.get(0);
		List<CandidatePlan> stableCandidates = List.copyOf(candidates);
		return new CandidatePlan(index -> {
			List<IngredientFilterItemIndex.ItemEntry> smallest = null;
			for (CandidatePlan candidate : stableCandidates) {
				List<IngredientFilterItemIndex.ItemEntry> entries = candidate.collectCandidates(index);
				if (smallest == null || entries.size() < smallest.size()) {
					smallest = entries;
				}
			}
			return smallest == null ? index.orderedEntries() : smallest;
		});
	}

	private static ItemQueryPlan compileNot(GroupFilter child) {
		return FilterTypeScope.declared(child).contains("item") ? FULL_SCAN : EMPTY;
	}

	private static ItemQueryPlan compileId(GroupFilter.Id id) {
		if (!isItemType(id.ingredientType())) return EMPTY;
		ResourceLocation resourceLocation = ResourceLocation.tryParse(id.id());
		if (resourceLocation == null) return EMPTY;
		return new CandidatePlan(index -> index.byId(resourceLocation));
	}

	private static ItemQueryPlan compileTag(GroupFilter.Tag tag) {
		if (!isItemType(tag.ingredientType())) return EMPTY;
		ResourceLocation tagId = ResourceLocation.tryParse(tag.tag());
		if (tagId == null) return EMPTY;
		return new CandidatePlan(index -> index.byTag(tagId));
	}

	private static ItemQueryPlan compileBlockTag(GroupFilter.BlockTag blockTag) {
		ResourceLocation tagId = ResourceLocation.tryParse(blockTag.tag());
		if (tagId == null) return EMPTY;
		return new CandidatePlan(index -> index.byBlockTag(tagId));
	}

	private static ItemQueryPlan compileNamespace(GroupFilter.Namespace namespace) {
		if (!isItemType(namespace.ingredientType())) return EMPTY;
		return new CandidatePlan(index -> index.byNamespace(namespace.namespace()));
	}

	private static ItemQueryPlan compileExactStack(GroupFilter.ExactStack exactStack) {
		return GroupItemSelector.decodeExactSelector("stack:" + exactStack.encodedStack())
			.map(reference -> {
				ResourceLocation id = BuiltInRegistries.ITEM.getKey(reference.getItem());
				if (id == null) return (ItemQueryPlan) EMPTY;
				ItemStack normalized = GroupItemSelector.normalizedCopy(reference);
				return (ItemQueryPlan) new CandidatePlan(index -> {
					List<IngredientFilterItemIndex.ItemEntry> bucket = index.byId(id);
					if (bucket.isEmpty()) return List.of();
					List<IngredientFilterItemIndex.ItemEntry> matches = new ArrayList<>();
					for (IngredientFilterItemIndex.ItemEntry entry : bucket) {
						if (ItemStack.isSameItemSameTags(normalized, entry.stack())) {
							matches.add(entry);
						}
					}
					return List.copyOf(matches);
				});
			})
			.orElse(EMPTY);
	}

	private static boolean isItemType(String type) {
		return "item".equals(canonicalType(type));
	}

	private static String canonicalType(String type) {
		String canonical = IngredientTypeIds.getCanonicalId(type);
		return canonical != null ? canonical : type;
	}

	public sealed interface ItemQueryPlan
		permits EmptyPlan, AllItemsPlan, CandidatePlan, FullScanPlan {
	}

	public record EmptyPlan() implements ItemQueryPlan {}
	public record AllItemsPlan() implements ItemQueryPlan {}
	public record FullScanPlan() implements ItemQueryPlan {}

	@FunctionalInterface
	public interface CandidateCollector {
		List<IngredientFilterItemIndex.ItemEntry> collect(IngredientFilterItemIndex index);
	}

	public record CandidatePlan(CandidateCollector collector) implements ItemQueryPlan {
		public List<IngredientFilterItemIndex.ItemEntry> collectCandidates(IngredientFilterItemIndex index) {
			return collector.collect(index);
		}
	}
}
