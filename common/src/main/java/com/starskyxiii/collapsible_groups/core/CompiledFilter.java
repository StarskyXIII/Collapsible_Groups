package com.starskyxiii.collapsible_groups.core;

import com.starskyxiii.collapsible_groups.compat.jei.api.IngredientTypeRegistry;
import net.minecraft.resources.Identifier;

import java.util.List;

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
			case GroupFilter.Any any -> new AnyNode(any.children().stream().map(CompiledFilter::compileNode).toList());
			case GroupFilter.All all -> new AllNode(all.children().stream().map(CompiledFilter::compileNode).toList());
			case GroupFilter.Not not -> new NotNode(compileNode(not.child()));
			case GroupFilter.Id id -> new IdNode(canonicalType(id.ingredientType()), Identifier.parse(id.id()));
			case GroupFilter.Tag tag -> new TagNode(canonicalType(tag.ingredientType()), Identifier.parse(tag.tag()));
			case GroupFilter.BlockTag blockTag -> new BlockTagNode(Identifier.parse(blockTag.tag()));
			case GroupFilter.ItemPathStartsWith startsWith -> new ItemPathStartsWithNode(startsWith.prefix());
			case GroupFilter.ItemPathEndsWith endsWith -> new ItemPathEndsWithNode(endsWith.suffix());
			case GroupFilter.Namespace namespace -> new NamespaceNode(canonicalType(namespace.ingredientType()), namespace.namespace());
			case GroupFilter.ExactStack exactStack -> new ExactStackNode(exactStack.encodedStack());
			case GroupFilter.HasComponent hc -> new HasComponentNode(hc.componentTypeId(), hc.encodedValue());
			case GroupFilter.ComponentPath cp -> new ComponentPathNode(cp.componentTypeId(), cp.path(), cp.expectedValue());
		};
	}

	private static String canonicalType(String type) {
		String canonical = IngredientTypeRegistry.getCanonicalId(type);
		return canonical != null ? canonical : type;
	}

	private sealed interface CompiledNode
		permits AnyNode, AllNode, NotNode, IdNode, TagNode, BlockTagNode, ItemPathStartsWithNode, ItemPathEndsWithNode, NamespaceNode, ExactStackNode, HasComponentNode, ComponentPathNode {
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

	private record IdNode(String ingredientType, Identifier id) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return sameType(ingredientType, view) && id.equals(view.resourceLocation());
		}
	}

	private record TagNode(String ingredientType, Identifier tagId) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return sameType(ingredientType, view) && view.hasTag(tagId);
		}
	}

	private record BlockTagNode(Identifier tagId) implements CompiledNode {
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
			Identifier resourceLocation = view.resourceLocation();
			return resourceLocation != null && resourceLocation.getPath().startsWith(prefix);
		}
	}

	private record ItemPathEndsWithNode(String suffix) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			if (!sameType("item", view)) {
				return false;
			}
			Identifier resourceLocation = view.resourceLocation();
			return resourceLocation != null && resourceLocation.getPath().endsWith(suffix);
		}
	}

	private record NamespaceNode(String ingredientType, String namespace) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			if (!sameType(ingredientType, view)) {
				return false;
			}
			Identifier resourceLocation = view.resourceLocation();
			return resourceLocation != null && namespace.equals(resourceLocation.getNamespace());
		}
	}

	private record ExactStackNode(String encodedStack) implements CompiledNode {
		@Override
		public boolean matches(IngredientView view) {
			return sameType("item", view) && view.matchesExactStack(encodedStack);
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
