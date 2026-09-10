package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;

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
		return switch (filter) {
			case GroupFilter.Any any -> compileAny(any);
			case GroupFilter.All all -> new AllNode(all.children().stream().map(CompiledFilter::compileNode).toList());
			case GroupFilter.Not not -> new NotNode(compileNode(not.child()), FilterTypeScope.declared(not.child()));
			case GroupFilter.Id id -> new IdNode(canonicalType(id.ingredientType()), ResourceLocation.parse(id.id()));
			case GroupFilter.Tag tag -> new TagNode(canonicalType(tag.ingredientType()), ResourceLocation.parse(tag.tag()));
			case GroupFilter.BlockTag blockTag -> new BlockTagNode(ResourceLocation.parse(blockTag.tag()));
			case GroupFilter.ItemPathStartsWith startsWith -> new ItemPathStartsWithNode(startsWith.prefix());
			case GroupFilter.ItemPathContains contains -> new ItemPathContainsNode(contains.needle());
			case GroupFilter.ItemPathEndsWith endsWith -> new ItemPathEndsWithNode(endsWith.suffix());
			case GroupFilter.Namespace namespace -> new NamespaceNode(canonicalType(namespace.ingredientType()), namespace.namespace());
			case GroupFilter.ExactStack exactStack -> validExactPayload(exactStack)
                ? new ExactStackSetNode(List.of(exactStack.encodedStack())) : UnavailableNode.INSTANCE;
			case GroupFilter.HasComponent hc -> new HasComponentNode(hc.componentTypeId(), hc.encodedValue(), hc.payload());
			case GroupFilter.ComponentPath cp -> new ComponentPathNode(cp.componentTypeId(), cp.path(), cp.expectedValue(), cp.payload());
			case GroupFilter.Unsupported ignored -> UnavailableNode.INSTANCE;
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
			} else if (child instanceof GroupFilter.ExactStack exact && validExactPayload(exact)) {
				List<String> encodedStacks = new ArrayList<>();
				int j = i;
				while (j < size && children.get(j) instanceof GroupFilter.ExactStack exactStack && validExactPayload(exactStack)) {
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

    private static boolean validExactPayload(GroupFilter.ExactStack stack) {
        return stack.payload() == null || ItemDataPayload.ITEM_COMPONENTS.equals(stack.payload().dataFormat())
            && stack.payload().data().isJsonObject();
    }

	private static String canonicalType(String type) {
		String canonical = IngredientTypeIds.getCanonicalId(type);
		return canonical != null ? canonical : type;
	}

	private sealed interface CompiledNode
		permits AnyNode, AllNode, NotNode, IdNode, IdSetNode, TagNode, BlockTagNode, ItemPathStartsWithNode, ItemPathContainsNode, ItemPathEndsWithNode, NamespaceNode, ExactStackSetNode, HasComponentNode, ComponentPathNode, UnavailableNode {
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

	private static final class ExactStackSetNode implements CompiledNode {
		private static final String STACK_PREFIX = "stack:";

		private final ExactStackMatcherCache<ItemStack> cache;

		ExactStackSetNode(List<String> encodedStacks) {
			this.cache = new ExactStackMatcherCache<>(encodedStacks, () -> {
				GroupItemSelector.ExactDecodeContext context = GroupItemSelector.exactDecodeContext();
				return new ExactStackMatcherCache.DecodeAttempt<>() {
					@Override public boolean liveRegistry() { return context.liveRegistry(); }
					@Override public Object registryIdentity() { return context.registryIdentity(); }
					@Override public Optional<ExactStackMatcherCache.Decoded<ItemStack>> decode(String encodedStack) {
						return GroupItemSelector.decodeExactSelector(STACK_PREFIX + encodedStack, context)
							.map(stack -> new ExactStackMatcherCache.Decoded<>(
								BuiltInRegistries.ITEM.getKey(stack.getItem()), stack));
					}
				};
			}, () -> GroupItemSelector.registryIdentity());
		}

		@Override
		public Evaluation evaluate(IngredientView view) {
			// Type gate first: a non-item view must never trigger initialization or decode.
			if (!sameType("item", view)) {
				return Evaluation.NO_MATCH;
			}
			ResourceLocation resourceLocation = view.resourceLocation();
			return cache.evaluate(resourceLocation, view::matchesDecodedExactStack);
		}
	}

    private record HasComponentNode(String componentTypeId, String encodedValue, ItemDataPayload payload) implements CompiledNode {
        @Override
        public Evaluation evaluate(IngredientView view) {
            return sameType("item", view) ? view.queryComponent(componentTypeId, encodedValue, payload) : Evaluation.NO_MATCH;
        }
    }

    private record ComponentPathNode(String componentTypeId, String path, String expectedValue, ItemDataPayload payload) implements CompiledNode {
        @Override
        public Evaluation evaluate(IngredientView view) {
            return sameType("item", view) ? view.queryComponentPath(componentTypeId, path, expectedValue, payload) : Evaluation.NO_MATCH;
        }
    }

	private static boolean sameType(String ingredientType, IngredientView view) {
		return canonicalType(ingredientType).equals(canonicalType(view.ingredientType()));
	}
}
