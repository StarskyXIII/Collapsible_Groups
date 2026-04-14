package com.starskyxiii.collapsible_groups.core;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Mutable tree draft used by the in-game rules editor.
 *
 * <p>Unlike {@link GroupFilterEditorDraft}, this model preserves the full filter
 * hierarchy, including nested {@code any/all/not} structures and the atomic
 * nodes that cannot participate in the content-tab quick-edit flow.
 */
public final class GroupFilterRuleDraft {
	public enum NodeKind {
		ANY(true, 1, Integer.MAX_VALUE),
		ALL(true, 1, Integer.MAX_VALUE),
		NOT(true, 0, 1),
		ID(false, 0, 0),
		TAG(false, 0, 0),
		BLOCK_TAG(false, 0, 0),
		ITEM_PATH_STARTS_WITH(false, 0, 0),
		ITEM_PATH_ENDS_WITH(false, 0, 0),
		NAMESPACE(false, 0, 0),
		EXACT_STACK(false, 0, 0),
		HAS_COMPONENT(false, 0, 0),
		COMPONENT_PATH(false, 0, 0);

		private final boolean compound;
		private final int minChildren;
		private final int maxChildren;

		NodeKind(boolean compound, int minChildren, int maxChildren) {
			this.compound = compound;
			this.minChildren = minChildren;
			this.maxChildren = maxChildren;
		}

		public boolean compound() {
			return compound;
		}

		public int minChildren() {
			return minChildren;
		}

		public int maxChildren() {
			return maxChildren;
		}
	}

	public static final class Node {
		private NodeKind kind;
		private Node parent;
		private final List<Node> children = new ArrayList<>();
		private String ingredientType = "item";
		private String primaryValue = "";
		private String secondaryValue = "";
		private String tertiaryValue = "";

		private Node(NodeKind kind) {
			this.kind = Objects.requireNonNull(kind, "kind");
		}

		public NodeKind kind() {
			return kind;
		}

		public void setKind(NodeKind kind) {
			this.kind = Objects.requireNonNull(kind, "kind");
			if (!kind.compound()) {
				this.children.clear();
			} else if (children.size() > kind.maxChildren()) {
				children.subList(kind.maxChildren(), children.size()).clear();
			}
		}

		public @Nullable Node parent() {
			return parent;
		}

		public List<Node> children() {
			return children;
		}

		public boolean canAcceptChild() {
			return kind.compound() && children.size() < kind.maxChildren();
		}

		public String ingredientType() {
			return ingredientType;
		}

		public void setIngredientType(String ingredientType) {
			this.ingredientType = ingredientType == null ? "" : ingredientType;
		}

		public String primaryValue() {
			return primaryValue;
		}

		public void setPrimaryValue(String primaryValue) {
			this.primaryValue = primaryValue == null ? "" : primaryValue;
		}

		public String secondaryValue() {
			return secondaryValue;
		}

		public void setSecondaryValue(String secondaryValue) {
			this.secondaryValue = secondaryValue == null ? "" : secondaryValue;
		}

		public String tertiaryValue() {
			return tertiaryValue;
		}

		public void setTertiaryValue(String tertiaryValue) {
			this.tertiaryValue = tertiaryValue == null ? "" : tertiaryValue;
		}
	}

	public record FlatNode(Node node, int depth) {}

	private Node root;

	public static GroupFilterRuleDraft empty() {
		return new GroupFilterRuleDraft();
	}

	public static GroupFilterRuleDraft decode(@Nullable GroupFilter filter) {
		GroupFilterRuleDraft draft = new GroupFilterRuleDraft();
		if (filter != null) {
			draft.root = decodeNode(GroupFilterNormalizer.normalize(filter));
		}
		return draft;
	}

	public @Nullable Node root() {
		return root;
	}

	public boolean hasRoot() {
		return root != null;
	}

	public void clear() {
		root = null;
	}

	public void replaceWith(GroupFilterRuleDraft other) {
		Objects.requireNonNull(other, "other");
		root = other.root == null ? null : copyNode(other.root, null);
	}

	public Node createNode(NodeKind kind) {
		return new Node(kind);
	}

	public Node setRoot(NodeKind kind) {
		Node node = createNode(kind);
		root = node;
		return node;
	}

	public Optional<GroupFilter> toFilter() {
		return root == null ? Optional.empty() : Optional.ofNullable(encodeNode(root));
	}

	public List<FlatNode> flatten() {
		if (root == null) {
			return List.of();
		}
		List<FlatNode> out = new ArrayList<>();
		flatten(root, 0, out);
		return List.copyOf(out);
	}

	public boolean canInsertRelativeTo(@Nullable Node selection) {
		if (root == null) {
			return true;
		}
		if (selection == null) {
			return false;
		}
		if (selection.canAcceptChild()) {
			return true;
		}
		Node parent = selection.parent();
		return parent != null && parent.canAcceptChild();
	}

	public @Nullable Node insertRelativeTo(@Nullable Node selection, NodeKind kind) {
		Node created = createNode(kind);
		if (root == null) {
			root = created;
			return created;
		}
		if (selection == null) {
			return null;
		}
		if (selection.canAcceptChild()) {
			attachChild(selection, created);
			return created;
		}
		Node parent = selection.parent();
		if (parent == null || !parent.canAcceptChild()) {
			return null;
		}
		attachChild(parent, created);
		return created;
	}

	public boolean canWrap(@Nullable Node selection, NodeKind wrapperKind) {
		if (selection == null) {
			return false;
		}
		return wrapperKind.compound();
	}

	public @Nullable Node wrap(Node selection, NodeKind wrapperKind) {
		if (!canWrap(selection, wrapperKind)) {
			return null;
		}

		Node wrapper = createNode(wrapperKind);
		Node parent = selection.parent();
		if (parent == null) {
			root = wrapper;
		} else {
			int index = parent.children.indexOf(selection);
			if (index < 0) {
				return null;
			}
			parent.children.set(index, wrapper);
			wrapper.parent = parent;
		}

		selection.parent = wrapper;
		wrapper.children.add(selection);
		return wrapper;
	}

	public @Nullable Node delete(Node node) {
		Objects.requireNonNull(node, "node");
		Node parent = node.parent();
		if (parent == null) {
			root = null;
			return null;
		}

		parent.children.remove(node);
		node.parent = null;
		return parent;
	}

	private static void flatten(Node node, int depth, List<FlatNode> out) {
		out.add(new FlatNode(node, depth));
		for (Node child : node.children) {
			flatten(child, depth + 1, out);
		}
	}

	private static void attachChild(Node parent, Node child) {
		if (!parent.canAcceptChild()) {
			return;
		}
		child.parent = parent;
		parent.children.add(child);
	}

	private static Node copyNode(Node source, @Nullable Node parent) {
		Node copy = new Node(source.kind);
		copy.parent = parent;
		copy.ingredientType = source.ingredientType;
		copy.primaryValue = source.primaryValue;
		copy.secondaryValue = source.secondaryValue;
		copy.tertiaryValue = source.tertiaryValue;
		for (Node child : source.children) {
			copy.children.add(copyNode(child, copy));
		}
		return copy;
	}

	private static Node decodeNode(GroupFilter filter) {
		return switch (filter) {
			case GroupFilter.Any any -> {
				Node node = new Node(NodeKind.ANY);
				any.children().forEach(child -> attachChild(node, decodeNode(child)));
				yield node;
			}
			case GroupFilter.All all -> {
				Node node = new Node(NodeKind.ALL);
				all.children().forEach(child -> attachChild(node, decodeNode(child)));
				yield node;
			}
			case GroupFilter.Not not -> {
				Node node = new Node(NodeKind.NOT);
				attachChild(node, decodeNode(not.child()));
				yield node;
			}
			case GroupFilter.Id id -> {
				Node node = new Node(NodeKind.ID);
				node.ingredientType = id.ingredientType();
				node.primaryValue = id.id();
				yield node;
			}
			case GroupFilter.Tag tag -> {
				Node node = new Node(NodeKind.TAG);
				node.ingredientType = tag.ingredientType();
				node.primaryValue = tag.tag();
				yield node;
			}
			case GroupFilter.BlockTag blockTag -> {
				Node node = new Node(NodeKind.BLOCK_TAG);
				node.primaryValue = blockTag.tag();
				yield node;
			}
			case GroupFilter.ItemPathStartsWith startsWith -> {
				Node node = new Node(NodeKind.ITEM_PATH_STARTS_WITH);
				node.primaryValue = startsWith.prefix();
				yield node;
			}
			case GroupFilter.ItemPathEndsWith endsWith -> {
				Node node = new Node(NodeKind.ITEM_PATH_ENDS_WITH);
				node.primaryValue = endsWith.suffix();
				yield node;
			}
			case GroupFilter.Namespace namespace -> {
				Node node = new Node(NodeKind.NAMESPACE);
				node.ingredientType = namespace.ingredientType();
				node.primaryValue = namespace.namespace();
				yield node;
			}
			case GroupFilter.ExactStack exactStack -> {
				Node node = new Node(NodeKind.EXACT_STACK);
				node.primaryValue = exactStack.encodedStack();
				yield node;
			}
			case GroupFilter.HasComponent hasComponent -> {
				Node node = new Node(NodeKind.HAS_COMPONENT);
				node.primaryValue = hasComponent.componentTypeId();
				node.secondaryValue = hasComponent.encodedValue();
				yield node;
			}
			case GroupFilter.ComponentPath componentPath -> {
				Node node = new Node(NodeKind.COMPONENT_PATH);
				node.primaryValue = componentPath.componentTypeId();
				node.secondaryValue = componentPath.path();
				node.tertiaryValue = componentPath.expectedValue();
				yield node;
			}
		};
	}

	private static @Nullable GroupFilter encodeNode(Node node) {
		return switch (node.kind) {
			case ANY -> encodeCompound(node, true);
			case ALL -> encodeCompound(node, false);
			case NOT -> {
				if (node.children.size() != 1) {
					yield null;
				}
				GroupFilter child = encodeNode(node.children.getFirst());
				yield child == null ? null : Filters.not(child);
			}
			case ID -> Filters.id(node.ingredientType, node.primaryValue);
			case TAG -> Filters.tag(node.ingredientType, node.primaryValue);
			case BLOCK_TAG -> Filters.blockTag(node.primaryValue);
			case ITEM_PATH_STARTS_WITH -> Filters.itemPathStartsWith(node.primaryValue);
			case ITEM_PATH_ENDS_WITH -> Filters.itemPathEndsWith(node.primaryValue);
			case NAMESPACE -> Filters.namespace(node.ingredientType, node.primaryValue);
			case EXACT_STACK -> Filters.exactStack(node.primaryValue);
			case HAS_COMPONENT -> Filters.itemComponent(node.primaryValue, node.secondaryValue);
			case COMPONENT_PATH -> Filters.itemComponentPath(node.primaryValue, node.secondaryValue, node.tertiaryValue);
		};
	}

	private static @Nullable GroupFilter encodeCompound(Node node, boolean any) {
		if (node.children.size() < node.kind.minChildren()) {
			return null;
		}
		List<GroupFilter> children = new ArrayList<>(node.children.size());
		for (Node child : node.children) {
			GroupFilter encoded = encodeNode(child);
			if (encoded == null) {
				return null;
			}
			children.add(encoded);
		}
		if (children.isEmpty()) {
			return null;
		}
		return any
			? Filters.any(children.toArray(GroupFilter[]::new))
			: Filters.all(children.toArray(GroupFilter[]::new));
	}
}
