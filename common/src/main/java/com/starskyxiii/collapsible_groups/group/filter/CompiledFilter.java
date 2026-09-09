package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.internal.version.data.Minecraft1201NbtAccess;
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
	public enum Evaluation {
		MATCH,
		NO_MATCH,
		UNAVAILABLE
	}

	private final GroupFilter source;
	private final CompiledNode root;
	private final FilterTypeScope candidateTypes;

	private CompiledFilter(GroupFilter source, CompiledNode root) {
		this.source = source;
		this.root = root;
		this.candidateTypes = FilterTypeScope.candidates(source);
	}

	public static CompiledFilter compile(GroupFilter filter) {
		return new CompiledFilter(filter, compileNode(filter));
	}

	public boolean matches(IngredientView view) {
		return evaluate(view) == Evaluation.MATCH;
	}

	public Evaluation evaluate(IngredientView view) {
		return root.evaluate(view);
	}

	public GroupFilter source() {
		return source;
	}

	public FilterTypeScope candidateTypes() {
		return candidateTypes;
	}

	private static CompiledNode compileNode(GroupFilter filter) {
		if (!FilterNodeCapabilities.isAvailable(FilterNodeCapabilities.kindOf(filter))) {
			return UnavailableNode.INSTANCE;
		}
		if (filter instanceof GroupFilter.Any) return compileAny((GroupFilter.Any) filter);
		if (filter instanceof GroupFilter.All) return new AllNode(((GroupFilter.All) filter).children().stream().map(CompiledFilter::compileNode).toList());
		if (filter instanceof GroupFilter.Not) { GroupFilter.Not value = (GroupFilter.Not) filter; return new NotNode(compileNode(value.child()), FilterTypeScope.declared(value.child())); }
		if (filter instanceof GroupFilter.Id) { GroupFilter.Id value = (GroupFilter.Id) filter; return new IdNode(canonicalType(value.ingredientType()), new ResourceLocation(value.id())); }
		if (filter instanceof GroupFilter.Tag) { GroupFilter.Tag value = (GroupFilter.Tag) filter; return new TagNode(canonicalType(value.ingredientType()), new ResourceLocation(value.tag())); }
		if (filter instanceof GroupFilter.BlockTag) return new BlockTagNode(new ResourceLocation(((GroupFilter.BlockTag) filter).tag()));
		if (filter instanceof GroupFilter.ItemPathStartsWith) return new ItemPathStartsWithNode(((GroupFilter.ItemPathStartsWith) filter).prefix());
		if (filter instanceof GroupFilter.ItemPathContains) return new ItemPathContainsNode(((GroupFilter.ItemPathContains) filter).needle());
		if (filter instanceof GroupFilter.ItemPathEndsWith) return new ItemPathEndsWithNode(((GroupFilter.ItemPathEndsWith) filter).suffix());
		if (filter instanceof GroupFilter.Namespace) { GroupFilter.Namespace value = (GroupFilter.Namespace) filter; return new NamespaceNode(canonicalType(value.ingredientType()), value.namespace()); }
		if (filter instanceof GroupFilter.ExactStack) return new ExactStackSetNode(List.of(((GroupFilter.ExactStack) filter).encodedStack()));
		if (filter instanceof GroupFilter.Nbt) return new NbtNode(((GroupFilter.Nbt) filter).expectedSnbt());
		if (filter instanceof GroupFilter.NbtPath) { GroupFilter.NbtPath value = (GroupFilter.NbtPath) filter; return new NbtPathNode(value.path(), value.expectedSnbt()); }
		if (filter instanceof GroupFilter.HasComponent) { GroupFilter.HasComponent value = (GroupFilter.HasComponent) filter; return new HasComponentNode(value.componentTypeId(), value.encodedValue()); }
		if (filter instanceof GroupFilter.ComponentPath) { GroupFilter.ComponentPath value = (GroupFilter.ComponentPath) filter; return new ComponentPathNode(value.componentTypeId(), value.path(), value.expectedValue()); }
		return UnavailableNode.INSTANCE;
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
						.add(new ResourceLocation(idFilter.id()));
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
		permits AnyNode, AllNode, NotNode, IdNode, IdSetNode, TagNode, BlockTagNode, ItemPathStartsWithNode, ItemPathContainsNode, ItemPathEndsWithNode, NamespaceNode, ExactStackSetNode, NbtNode, NbtPathNode, HasComponentNode, ComponentPathNode, UnavailableNode {
		default Evaluation evaluate(IngredientView view) {
			return matches(view) ? Evaluation.MATCH : Evaluation.NO_MATCH;
		}

		default boolean matches(IngredientView view) {
			throw new UnsupportedOperationException("Node must implement evaluate or matches");
		}
	}

	private record AnyNode(List<CompiledNode> children) implements CompiledNode {
		@Override
		public Evaluation evaluate(IngredientView view) {
			boolean unavailable = false;
			for (CompiledNode child : children) {
				Evaluation result = child.evaluate(view);
				if (result == Evaluation.MATCH) return Evaluation.MATCH;
				unavailable |= result == Evaluation.UNAVAILABLE;
			}
			return unavailable ? Evaluation.UNAVAILABLE : Evaluation.NO_MATCH;
		}
	}

	private record AllNode(List<CompiledNode> children) implements CompiledNode {
		@Override
		public Evaluation evaluate(IngredientView view) {
			boolean unavailable = false;
			for (CompiledNode child : children) {
				Evaluation result = child.evaluate(view);
				if (result == Evaluation.NO_MATCH) return Evaluation.NO_MATCH;
				unavailable |= result == Evaluation.UNAVAILABLE;
			}
			return unavailable ? Evaluation.UNAVAILABLE : Evaluation.MATCH;
		}
	}

	private record NotNode(CompiledNode child, FilterTypeScope domain) implements CompiledNode {
		@Override
		public Evaluation evaluate(IngredientView view) {
			if (domain.isEmpty()) return Evaluation.UNAVAILABLE;
			if (!domain.contains(view.ingredientType())) return Evaluation.NO_MATCH;
			return switch (child.evaluate(view)) {
				case MATCH -> Evaluation.NO_MATCH;
				case NO_MATCH -> Evaluation.MATCH;
				case UNAVAILABLE -> Evaluation.UNAVAILABLE;
			};
		}
	}

	private enum UnavailableNode implements CompiledNode {
		INSTANCE;

		@Override
		public Evaluation evaluate(IngredientView view) {
			return Evaluation.UNAVAILABLE;
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
			String viewType = canonicalType(view.ingredientType());
			Set<ResourceLocation> ids = idsByType.get(viewType);
			if (ids != null && ids.contains(resourceLocation)) return true;
			return idsByType.entrySet().stream().anyMatch(entry ->
				canonicalType(entry.getKey()).equals(viewType) && entry.getValue().contains(resourceLocation));
		}
	}

	private record TagNode(String ingredientType, ResourceLocation tagId) implements CompiledNode {
		@Override
		public Evaluation evaluate(IngredientView view) {
			if (!sameType(ingredientType, view)) return Evaluation.NO_MATCH;
			return switch (view.queryTag(tagId)) {
				case MATCH -> Evaluation.MATCH;
				case NO_MATCH -> Evaluation.NO_MATCH;
				case UNAVAILABLE -> Evaluation.UNAVAILABLE;
			};
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

	/**
	 * Folded representation of a maximal contiguous run of {@code ExactStack} children
	 * within an {@code Any}. The run's encoded selectors are decoded at most once (lazily, on the
	 * first {@code item}-typed evaluation) into a base-id → decoded-reference bucket, replacing the
	 * former per-evaluation JSON+codec decode and paired {@code normalizedCopy}. A match is then an
	 * O(1) map lookup on the candidate's item id
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
	 * {@link List#copyOf} lists) is published once through a volatile field via double-checked
	 * locking; a reader observes either {@code null} (not yet built /
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

		private final ExactStackMatcherCache<ItemStack> cache;

		ExactStackSetNode(List<String> encodedStacks) {
			this.cache = new ExactStackMatcherCache<>(encodedStacks, () -> {
				final GroupItemSelector.ExactDecodeContext decodeContext = GroupItemSelector.exactDecodeContext();
				return new ExactStackMatcherCache.DecodeAttempt<ItemStack>() {
					@Override public boolean liveRegistry() { return decodeContext.liveRegistry(); }
					@Override public Object registryIdentity() { return decodeContext.registryIdentity(); }
					@Override public Optional<ExactStackMatcherCache.Decoded<ItemStack>> decode(String encodedStack) {
						return GroupItemSelector.decodeExactSelector(STACK_PREFIX + encodedStack, decodeContext)
							.map(stack -> new ExactStackMatcherCache.Decoded<>(
								BuiltInRegistries.ITEM.getKey(stack.getItem()), stack));
					}
				};
			}, () -> GroupItemSelector.registryIdentity());
		}

		@Override
		public boolean matches(IngredientView view) {
			// Type gate first: a non-item view must never trigger initialization or decode.
			if (!sameType("item", view)) {
				return false;
			}
			ResourceLocation resourceLocation = view.resourceLocation();
			return cache.matches(resourceLocation, view::matchesDecodedExactStack);
		}
	}

	private record HasComponentNode(String componentTypeId, String encodedValue) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return sameType("item", view) && view.hasComponent(componentTypeId, encodedValue);
		}
	}

	private static final class NbtNode implements CompiledNode {
		private final Optional<Minecraft1201NbtAccess.Matcher> matcher;

		private NbtNode(String expectedSnbt) {
			matcher = Minecraft1201NbtAccess.compileRoot(expectedSnbt);
		}

		@Override
		public Evaluation evaluate(IngredientView view) {
			if (!sameType("item", view)) return Evaluation.NO_MATCH;
			return matcher
				.map(value -> view.matchesNbt(value) ? Evaluation.MATCH : Evaluation.NO_MATCH)
				.orElse(Evaluation.UNAVAILABLE);
		}
	}

	private static final class NbtPathNode implements CompiledNode {
		private final Optional<Minecraft1201NbtAccess.Matcher> matcher;

		private NbtPathNode(String path, String expectedSnbt) {
			matcher = Minecraft1201NbtAccess.compilePath(path, expectedSnbt);
		}

		@Override
		public Evaluation evaluate(IngredientView view) {
			if (!sameType("item", view)) return Evaluation.NO_MATCH;
			return matcher
				.map(value -> view.matchesNbt(value) ? Evaluation.MATCH : Evaluation.NO_MATCH)
				.orElse(Evaluation.UNAVAILABLE);
		}
	}

	private record ComponentPathNode(String componentTypeId, String path, String expectedValue) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return sameType("item", view) && view.hasComponentPath(componentTypeId, path, expectedValue);
		}
	}

	private static boolean sameType(String ingredientType, IngredientView view) {
		return canonicalType(ingredientType).equals(canonicalType(view.ingredientType()));
	}
}
