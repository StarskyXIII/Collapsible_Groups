package com.starskyxiii.collapsible_groups.core;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CompiledFilter {
	private final GroupFilter source;
	private final CompiledNode root;

	private CompiledFilter(GroupFilter source, CompiledNode root) {
		this.source = source;
		this.root = root;
	}

	public static CompiledFilter compile(GroupFilter filter) {
		return new CompiledFilter(filter, compileNode(filter));
	}

	public boolean matches(IngredientView view) {
		return root.matches(view);
	}

	public GroupFilter source() {
		return source;
	}

	private static CompiledNode compileNode(GroupFilter filter) {
		return switch (filter) {
			case GroupFilter.Any any -> compileAny(any);
			case GroupFilter.All all -> new AllNode(all.children().stream().map(CompiledFilter::compileNode).toList());
			case GroupFilter.Not not -> new NotNode(compileNode(not.child()));
			case GroupFilter.Id id -> new IdNode(canonicalType(id.ingredientType()), ResourceLocation.parse(id.id()));
			case GroupFilter.Tag tag -> new TagNode(canonicalType(tag.ingredientType()), ResourceLocation.parse(tag.tag()));
			case GroupFilter.BlockTag blockTag -> new BlockTagNode(ResourceLocation.parse(blockTag.tag()));
			case GroupFilter.ItemPathStartsWith startsWith -> new ItemPathStartsWithNode(startsWith.prefix());
			case GroupFilter.ItemPathContains contains -> new ItemPathContainsNode(contains.needle());
			case GroupFilter.ItemPathEndsWith endsWith -> new ItemPathEndsWithNode(endsWith.suffix());
			case GroupFilter.Namespace namespace -> new NamespaceNode(canonicalType(namespace.ingredientType()), namespace.namespace());
			case GroupFilter.ExactStack exactStack -> new ExactStackNode(exactStack.encodedStack());
			case GroupFilter.HasComponent hc -> new HasComponentNode(hc.componentTypeId(), hc.encodedValue());
			case GroupFilter.ComponentPath cp -> new ComponentPathNode(cp.componentTypeId(), cp.path(), cp.expectedValue());
		};
	}

	/**
	 * Compiles an {@code Any} node, folding maximal contiguous runs of {@code Id} children into a
	 * single {@link IdSetNode} (keyed by {@link #canonicalType(String)}) and maximal contiguous
	 * runs of {@code ExactStack} children into a single {@link ExactStackSetNode}. Runs are
	 * detected in original encounter order; an {@code Id} run and an {@code ExactStack} run form
	 * separate nodes and any other child kind breaks the current run. All other children
	 * (Tag/Not/All/nested/path/component filters) are left in place and compiled + evaluated
	 * linearly, so the linear evaluation order is preserved.
	 *
	 * <p>Folding turns an O(N) linear scan over a large Id or ExactStack run into a single O(1)
	 * map lookup per {@code Any} evaluation. Match results are unchanged; however, a folded
	 * {@code ExactStack} run's immutable bucket is successfully built and published at most once
	 * (no decodes afterwards), while evaluations before publication — e.g. while the fallback
	 * registries are still in use — may re-decode and repeat decode warnings (see
	 * {@link ExactStackSetNode}). The number and timing of decode warnings for malformed exact
	 * selectors therefore changes, and it is <em>not</em> guaranteed that every observable decode
	 * side effect still occurs once per evaluation in strict linear order.
	 */
	private static CompiledNode compileAny(GroupFilter.Any any) {
		List<GroupFilter> children = any.children();
		List<CompiledNode> result = new ArrayList<>();
		int i = 0;
		int size = children.size();
		while (i < size) {
			GroupFilter child = children.get(i);
			if (child instanceof GroupFilter.Id) {
				Map<String, Set<ResourceLocation>> idsByType = new LinkedHashMap<>();
				int j = i;
				while (j < size && children.get(j) instanceof GroupFilter.Id idFilter) {
					idsByType
						.computeIfAbsent(canonicalType(idFilter.ingredientType()), type -> new LinkedHashSet<>())
						.add(ResourceLocation.parse(idFilter.id()));
					j++;
				}
				result.add(new IdSetNode(idsByType));
				i = j;
			} else if (child instanceof GroupFilter.ExactStack) {
				List<String> encodedStacks = new ArrayList<>();
				int j = i;
				while (j < size && children.get(j) instanceof GroupFilter.ExactStack exactStack) {
					encodedStacks.add(exactStack.encodedStack());
					j++;
				}
				result.add(new ExactStackSetNode(List.copyOf(encodedStacks)));
				i = j;
			} else {
				result.add(compileNode(child));
				i++;
			}
		}
		return new AnyNode(result);
	}

	private static String canonicalType(String type) {
		String canonical = IngredientTypeIds.getCanonicalId(type);
		return canonical != null ? canonical : type;
	}

	private sealed interface CompiledNode
		permits AnyNode, AllNode, NotNode, IdNode, IdSetNode, TagNode, BlockTagNode, ItemPathStartsWithNode, ItemPathContainsNode, ItemPathEndsWithNode, NamespaceNode, ExactStackNode, ExactStackSetNode, HasComponentNode, ComponentPathNode {
		boolean matches(IngredientView view);
	}

	private record AnyNode(List<CompiledNode> children) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return children.stream().anyMatch(child -> child.matches(view));
		}
	}

	private record AllNode(List<CompiledNode> children) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return children.stream().allMatch(child -> child.matches(view));
		}
	}

	private record NotNode(CompiledNode child) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return !child.matches(view);
		}
	}

	private record IdNode(String ingredientType, ResourceLocation id) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return sameType(ingredientType, view) && id.equals(view.resourceLocation());
		}
	}

	/**
	 * Folded representation of a maximal contiguous run of {@code Id} children within an
	 * {@code Any} node, grouped by {@link #canonicalType(String)}. {@code view.resourceLocation()
	 * == null} is checked first and short-circuits to {@code false} before any set lookup.
	 */
	private record IdSetNode(Map<String, Set<ResourceLocation>> idsByType) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			ResourceLocation resourceLocation = view.resourceLocation();
			if (resourceLocation == null) {
				return false;
			}
			Set<ResourceLocation> ids = idsByType.get(canonicalType(view.ingredientType()));
			return ids != null && ids.contains(resourceLocation);
		}
	}

	private record TagNode(String ingredientType, ResourceLocation tagId) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return sameType(ingredientType, view) && view.hasTag(tagId);
		}
	}

	private record BlockTagNode(ResourceLocation tagId) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return sameType("item", view) && view.hasBlockTag(tagId);
		}
	}

	private record ItemPathStartsWithNode(String prefix) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			if (!sameType("item", view)) {
				return false;
			}
			ResourceLocation resourceLocation = view.resourceLocation();
			return resourceLocation != null && resourceLocation.getPath().startsWith(prefix);
		}
	}

	private record ItemPathContainsNode(String needle) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			if (!sameType("item", view)) {
				return false;
			}
			ResourceLocation resourceLocation = view.resourceLocation();
			return resourceLocation != null && resourceLocation.getPath().contains(needle);
		}
	}

	private record ItemPathEndsWithNode(String suffix) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			if (!sameType("item", view)) {
				return false;
			}
			ResourceLocation resourceLocation = view.resourceLocation();
			return resourceLocation != null && resourceLocation.getPath().endsWith(suffix);
		}
	}

	private record NamespaceNode(String ingredientType, String namespace) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			if (!sameType(ingredientType, view)) {
				return false;
			}
			ResourceLocation resourceLocation = view.resourceLocation();
			return resourceLocation != null && namespace.equals(resourceLocation.getNamespace());
		}
	}

	private record ExactStackNode(String encodedStack) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return sameType("item", view) && view.matchesExactStack(encodedStack);
		}
	}

	/**
	 * Folded representation of a maximal contiguous run of {@code ExactStack} children
	 * within an {@code Any}. The run's encoded selectors are decoded at most once (lazily, on the
	 * first {@code item}-typed evaluation) into a base-id → decoded-reference bucket, replacing the
	 * per-evaluation JSON+codec decode and paired {@code normalizedCopy} that {@link ExactStackNode}
	 * performed for every ingredient. A match is then an O(1) map lookup on the candidate's item id
	 * plus a component deep-compare against the (usually single) reference for that id.
	 *
	 * <p><b>Type gate:</b> {@link #matches} short-circuits on a non-{@code item} view <em>before</em>
	 * any initialization or decode, so unrelated ingredient types never trigger bucket construction.
	 *
	 * <p><b>Registry readiness:</b> the whole run is decoded against a single
	 * {@link GroupItemSelector.ExactDecodeContext} snapshot, and the publication decision reads that
	 * same snapshot's {@code liveRegistry()} flag — never a fresh observation of {@code Minecraft}
	 * state, which could change between decode and decision (TOCTOU). If every selector in the batch
	 * fails <em>and</em> the batch actually decoded against the fallback registries, the bucket is
	 * not published and evaluation returns {@code false}, leaving the run to be retried on a later
	 * evaluation. Individual decode failures in a live-registry batch are treated as permanently
	 * invalid selectors (dropped from the bucket, never matching).
	 *
	 * <p><b>Publication:</b> the fully-built, deeply-immutable bucket ({@link Map#copyOf} of
	 * {@link List#copyOf} lists) is published once through the {@code volatile} {@link #bucket}
	 * field via double-checked locking; a reader observes either {@code null} (not yet built /
	 * awaiting a live registry) or the complete immutable map — never a partially populated one.
	 *
	 * <p><b>Observable side-effect change:</b> the immutable bucket is successfully built and
	 * published <em>at most once</em>; after publication no further decodes (or decode warnings)
	 * occur. However, while decoding keeps failing against the fallback registries (bucket not yet
	 * published), each evaluation re-attempts the decode and may log the same decode warnings
	 * again. Callers must not rely on decode-warning counts or timing, and this node does not
	 * preserve strict linear ordering of decode side effects relative to the surrounding
	 * {@code Any} children.
	 */
	private static final class ExactStackSetNode implements CompiledNode {
		private static final String STACK_PREFIX = "stack:";

		private final List<String> encodedStacks;
		private volatile Map<ResourceLocation, List<ItemStack>> bucket;

		ExactStackSetNode(List<String> encodedStacks) {
			this.encodedStacks = encodedStacks;
		}

		@Override
		public boolean matches(IngredientView view) {
			// Type gate first: a non-item view must never trigger initialization or decode.
			if (!sameType("item", view)) {
				return false;
			}
			Map<ResourceLocation, List<ItemStack>> resolved = resolveBucket();
			if (resolved == null) {
				return false; // registry not ready yet; retried on a later evaluation.
			}
			ResourceLocation resourceLocation = view.resourceLocation();
			if (resourceLocation == null) {
				return false;
			}
			List<ItemStack> references = resolved.get(resourceLocation);
			if (references == null) {
				return false;
			}
			for (ItemStack reference : references) {
				if (view.matchesDecodedExactStack(reference)) {
					return true;
				}
			}
			return false;
		}

		private Map<ResourceLocation, List<ItemStack>> resolveBucket() {
			Map<ResourceLocation, List<ItemStack>> local = bucket;
			if (local != null) {
				return local;
			}
			synchronized (this) {
				local = bucket;
				if (local != null) {
					return local;
				}
				Map<ResourceLocation, List<ItemStack>> built = buildBucket();
				if (built != null) {
					bucket = built; // one-shot publish of a fully immutable map.
				}
				return built;
			}
		}

		private Map<ResourceLocation, List<ItemStack>> buildBucket() {
			// Capture the registry resolution ONCE and use that same snapshot for every decode in
			// the run AND for the publication decision below. Re-observing Minecraft state after
			// the decodes would race game startup (TOCTOU): the connection could appear between an
			// all-failed fallback decode and the readiness check, permanently publishing an empty
			// bucket that never got a live-registry decode attempt.
			GroupItemSelector.ExactDecodeContext context = GroupItemSelector.exactDecodeContext();
			Map<ResourceLocation, List<ItemStack>> mutable = new LinkedHashMap<>();
			int decoded = 0;
			for (String encodedStack : encodedStacks) {
				Optional<ItemStack> reference = GroupItemSelector.decodeExactSelector(STACK_PREFIX + encodedStack, context);
				if (reference.isEmpty()) {
					continue;
				}
				ItemStack stack = reference.get(); // decode already applied normalizedCopy.
				ResourceLocation baseId = BuiltInRegistries.ITEM.getKey(stack.getItem());
				mutable.computeIfAbsent(baseId, id -> new ArrayList<>()).add(stack);
				decoded++;
			}
			// Don't cache an all-failed result when THIS batch actually decoded against the
			// fallback registries; retry on a later evaluation once a live registry is available.
			if (decoded == 0 && !context.liveRegistry()) {
				return null;
			}
			Map<ResourceLocation, List<ItemStack>> immutable = new LinkedHashMap<>();
			for (Map.Entry<ResourceLocation, List<ItemStack>> entry : mutable.entrySet()) {
				immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
			}
			return Map.copyOf(immutable);
		}
	}

	private record HasComponentNode(String componentTypeId, String encodedValue) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return sameType("item", view) && view.hasComponent(componentTypeId, encodedValue);
		}
	}

	private record ComponentPathNode(String componentTypeId, String path, String expectedValue) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return sameType("item", view) && view.hasComponentPath(componentTypeId, path, expectedValue);
		}
	}

	private static boolean sameType(String ingredientType, IngredientView view) {
		return ingredientType.equals(canonicalType(view.ingredientType()));
	}
}
