package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.compat.jei.api.IngredientTypeRegistry;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterNormalizer;
import com.starskyxiii.collapsible_groups.core.GroupItemSelector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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

	private static ItemQueryPlan compileNormalized(GroupFilter filter) {
		return switch (filter) {
			case GroupFilter.Any any -> compileAny(any.children());
			case GroupFilter.All all -> compileAll(all.children());
			case GroupFilter.Not not -> compileNot(not.child());
			case GroupFilter.Id id -> compileId(id);
			case GroupFilter.Tag tag -> compileTag(tag);
			case GroupFilter.BlockTag blockTag -> compileBlockTag(blockTag);
			case GroupFilter.ItemPathStartsWith ignored -> FULL_SCAN;
			case GroupFilter.ItemPathEndsWith ignored -> FULL_SCAN;
			case GroupFilter.Namespace namespace -> compileNamespace(namespace);
			case GroupFilter.ExactStack exactStack -> compileExactStack(exactStack);
			case GroupFilter.HasComponent ignored -> FULL_SCAN;
			case GroupFilter.ComponentPath ignored -> FULL_SCAN;
		};
	}

	private static ItemQueryPlan compileAny(List<GroupFilter> children) {
		List<CandidatePlan> candidates = new ArrayList<>();
		for (GroupFilter child : children) {
			ItemQueryPlan plan = compileNormalized(child);
			switch (plan) {
				case AllItemsPlan ignored -> {
					return ALL_ITEMS;
				}
				case EmptyPlan ignored -> {
					// no-op
				}
				case CandidatePlan candidate -> candidates.add(candidate);
				case FullScanPlan ignored -> {
					return FULL_SCAN;
				}
			}
		}
		if (candidates.isEmpty()) return EMPTY;
		if (candidates.size() == 1) return candidates.getFirst();
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
			switch (plan) {
				case EmptyPlan ignored -> {
					return EMPTY;
				}
				case AllItemsPlan ignored -> {
					// no-op
				}
				case CandidatePlan candidate -> {
					sawNonAllItems = true;
					candidates.add(candidate);
				}
				case FullScanPlan ignored -> {
					sawNonAllItems = true;
				}
			}
		}
		if (!sawNonAllItems) return ALL_ITEMS;
		if (candidates.isEmpty()) return FULL_SCAN;
		if (candidates.size() == 1) return candidates.getFirst();
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
		return switch (compileNormalized(child)) {
			case EmptyPlan ignored -> ALL_ITEMS;
			case AllItemsPlan ignored -> EMPTY;
			case CandidatePlan ignored -> FULL_SCAN;
			case FullScanPlan ignored -> FULL_SCAN;
		};
	}

	private static ItemQueryPlan compileId(GroupFilter.Id id) {
		if (!isItemType(id.ingredientType())) return EMPTY;
		Identifier resourceLocation = Identifier.tryParse(id.id());
		if (resourceLocation == null) return EMPTY;
		return new CandidatePlan(index -> index.byId(resourceLocation));
	}

	private static ItemQueryPlan compileTag(GroupFilter.Tag tag) {
		if (!isItemType(tag.ingredientType())) return EMPTY;
		Identifier tagId = Identifier.tryParse(tag.tag());
		if (tagId == null) return EMPTY;
		return new CandidatePlan(index -> index.byTag(tagId));
	}

	private static ItemQueryPlan compileBlockTag(GroupFilter.BlockTag blockTag) {
		Identifier tagId = Identifier.tryParse(blockTag.tag());
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
				Identifier id = BuiltInRegistries.ITEM.getKey(reference.getItem());
				if (id == null) return (ItemQueryPlan) EMPTY;
				ItemStack normalized = GroupItemSelector.normalizedCopy(reference);
				return (ItemQueryPlan) new CandidatePlan(index -> {
					List<IngredientFilterItemIndex.ItemEntry> bucket = index.byId(id);
					if (bucket.isEmpty()) return List.of();
					List<IngredientFilterItemIndex.ItemEntry> matches = new ArrayList<>();
					for (IngredientFilterItemIndex.ItemEntry entry : bucket) {
						if (ItemStack.isSameItemSameComponents(normalized, entry.stack())) {
							matches.add(entry);
						}
					}
					return List.copyOf(matches);
				});
			})
			.orElseGet(() -> GroupItemSelector.extractExactPayloadItemId(exactStack.encodedStack())
				.<ItemQueryPlan>map(id -> new CandidatePlan(index -> index.byId(id)))
				.orElse(EMPTY));
	}

	private static boolean isItemType(String type) {
		return "item".equals(canonicalType(type));
	}

	private static String canonicalType(String type) {
		String canonical = IngredientTypeRegistry.getCanonicalId(type);
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
