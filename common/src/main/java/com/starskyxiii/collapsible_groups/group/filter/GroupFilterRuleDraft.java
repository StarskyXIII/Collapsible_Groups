package com.starskyxiii.collapsible_groups.group.filter;

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
		ANY(FilterNodeKind.ANY),
		ALL(FilterNodeKind.ALL),
		NOT(FilterNodeKind.NOT),
		ID(FilterNodeKind.ID),
		TAG(FilterNodeKind.TAG),
		BLOCK_TAG(FilterNodeKind.BLOCK_TAG),
		ITEM_PATH_STARTS_WITH(FilterNodeKind.ITEM_PATH_STARTS_WITH),
		ITEM_PATH_CONTAINS(FilterNodeKind.ITEM_PATH_CONTAINS),
		ITEM_PATH_ENDS_WITH(FilterNodeKind.ITEM_PATH_ENDS_WITH),
		NAMESPACE(FilterNodeKind.NAMESPACE),
		EXACT_STACK(FilterNodeKind.EXACT_STACK),
		NBT(FilterNodeKind.NBT),
		NBT_PATH(FilterNodeKind.NBT_PATH),
		HAS_COMPONENT(FilterNodeKind.HAS_COMPONENT),
		COMPONENT_PATH(FilterNodeKind.COMPONENT_PATH);

		private final FilterNodeKind filterKind;

		NodeKind(FilterNodeKind filterKind) {
			this.filterKind = filterKind;
		}

		public FilterNodeKind filterKind() {
			return filterKind;
		}

		public boolean compound() {
			return descriptor().compound();
		}

		public int minChildren() {
			return descriptor().draftMinChildren();
		}

		public int maxChildren() {
			return descriptor().maxChildren();
		}

		private RuleDescriptor descriptor() {
			return RuleDescriptor.forKind(filterKind);
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

	public GroupFilterRuleDraft copy() {
		GroupFilterRuleDraft copy = new GroupFilterRuleDraft();
		copy.root = root == null ? null : copyNode(root, null);
		return copy;
	}

	public boolean contentEquals(GroupFilterRuleDraft other) {
		return other != null && nodesEqual(root, other.root);
	}

	public Optional<List<Integer>> pathOf(@Nullable Node node) {
		if (node == null) {
			return Optional.empty();
		}
		List<Integer> reversed = new ArrayList<>();
		Node current = node;
		while (current.parent != null) {
			int index = current.parent.children.indexOf(current);
			if (index < 0) {
				return Optional.empty();
			}
			reversed.add(index);
			current = current.parent;
		}
		if (current != root) {
			return Optional.empty();
		}
		List<Integer> path = new ArrayList<>(reversed.size());
		for (int i = reversed.size() - 1; i >= 0; i--) {
			path.add(reversed.get(i));
		}
		return Optional.of(List.copyOf(path));
	}

	public @Nullable Node nodeAtPath(List<Integer> path) {
		Objects.requireNonNull(path, "path");
		Node current = root;
		for (int index : path) {
			if (current == null || index < 0 || index >= current.children.size()) {
				return null;
			}
			current = current.children.get(index);
		}
		return current;
	}

	public Node createNode(NodeKind kind) {
		return new Node(kind);
	}

	private static boolean nodesEqual(@Nullable Node left, @Nullable Node right) {
		if (left == right) {
			return true;
		}
		if (left == null || right == null
			|| left.kind != right.kind
			|| !left.ingredientType.equals(right.ingredientType)
			|| !left.primaryValue.equals(right.primaryValue)
			|| !left.secondaryValue.equals(right.secondaryValue)
			|| !left.tertiaryValue.equals(right.tertiaryValue)
			|| left.children.size() != right.children.size()) {
			return false;
		}
		for (int i = 0; i < left.children.size(); i++) {
			if (!nodesEqual(left.children.get(i), right.children.get(i))) {
				return false;
			}
		}
		return true;
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

	/**
	 * Walks the parent chain of {@code node} to determine whether {@code maybeAncestor}
	 * lies on it. A node is considered a descendant of itself.
	 */
	public static boolean isDescendantOf(@Nullable Node node, @Nullable Node maybeAncestor) {
		if (node == null || maybeAncestor == null) {
			return false;
		}
		for (Node cursor = node; cursor != null; cursor = cursor.parent) {
			if (cursor == maybeAncestor) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether {@code node} may be moved to become a child of {@code targetParent}.
	 *
	 * <p>{@code targetParent} must be a compound node, {@code node} must not be the root
	 * (a rootless move is meaningless), the target must not be {@code node} itself, and the
	 * target must not sit inside {@code node}'s own subtree (which would form a cycle).
	 *
	 * <p>Capacity is only enforced when {@code node} is <em>joining</em> the target — a
	 * same-parent reorder ({@code node.parent == targetParent}) is exempt because the move
	 * detaches {@code node} first, so the child count never actually grows. The NOT
	 * single-child cap is therefore still enforced for cross-parent moves via
	 * {@link Node#canAcceptChild()}, while a NOT's lone child may still be reordered within
	 * it (a no-op, but must not be flatly rejected).
	 */
	public boolean canMove(@Nullable Node node, @Nullable Node targetParent) {
		if (node == null || targetParent == null) {
			return false;
		}
		if (node == root) {
			return false;
		}
		if (node == targetParent) {
			return false;
		}
		if (!targetParent.kind().compound()) {
			return false;
		}
		// Capacity only matters when actually joining a new parent; a same-parent reorder
		// detaches first so the count never grows.
		if (node.parent != targetParent && !targetParent.canAcceptChild()) {
			return false;
		}
		// A target inside node's own subtree would create a cycle.
		return !isDescendantOf(targetParent, node);
	}

	/**
	 * Moves {@code node} to become a child of {@code targetParent} at {@code index}.
	 *
	 * <p><b>Coordinate contract:</b> {@code index} is interpreted in the caller's
	 * <em>pre-detach</em> coordinate space — i.e. the child indices as they read <em>before</em>
	 * {@code node} is removed from its current parent. For a same-parent reorder this matters:
	 * removing {@code node} shifts every following sibling down by one, so a pre-detach target
	 * that sat after {@code node} must be decremented to keep pointing at the same visual gap.
	 * The order is fixed: first the same-parent {@code index--} adjustment, then a final clamp
	 * to {@code [0, post-detach size]}.
	 *
	 * <p>Cross-parent moves use the pre-detach index verbatim (the sentinel {@code oldIndex}
	 * for a foreign parent never matches, so the decrement never fires). Tail insertions passed
	 * as {@code children().size()} resolve to the post-detach tail: for a cross-parent move the
	 * clamp caps them at the new parent's size; for a same-parent tail move
	 * {@code oldIndex < size} holds so the decrement lands them at {@code size-1} = post-detach
	 * tail.
	 *
	 * @return {@code true} when the move was applied.
	 */
	public boolean moveNode(Node node, Node targetParent, int index) {
		if (!canMove(node, targetParent)) {
			return false;
		}
		Node oldParent = node.parent;
		int oldIndex = oldParent == null ? -1 : oldParent.children.indexOf(node);
		if (oldParent != null) {
			oldParent.children.remove(node);
		}
		// Pre-detach → post-detach: a same-parent target sitting after the removed node shifts
		// down by one. Sentinel oldIndex (-1 / foreign parent) never satisfies this, so
		// cross-parent moves keep the caller index verbatim.
		int adjusted = (oldParent == targetParent && oldIndex >= 0 && oldIndex < index) ? index - 1 : index;
		int clamped = Math.max(0, Math.min(adjusted, targetParent.children.size()));
		targetParent.children.add(clamped, node);
		node.parent = targetParent;
		return true;
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
		if (filter instanceof GroupFilter.Any) {
			GroupFilter.Any any = (GroupFilter.Any) filter;
				Node node = new Node(NodeKind.ANY);
				any.children().forEach(child -> attachChild(node, decodeNode(child)));
			return node;
		}
		if (filter instanceof GroupFilter.All) {
			GroupFilter.All all = (GroupFilter.All) filter;
				Node node = new Node(NodeKind.ALL);
				all.children().forEach(child -> attachChild(node, decodeNode(child)));
			return node;
		}
		if (filter instanceof GroupFilter.Not) {
			GroupFilter.Not not = (GroupFilter.Not) filter;
				Node node = new Node(NodeKind.NOT);
				attachChild(node, decodeNode(not.child()));
			return node;
		}
		if (filter instanceof GroupFilter.Id) {
			GroupFilter.Id id = (GroupFilter.Id) filter;
				Node node = new Node(NodeKind.ID);
				node.ingredientType = id.ingredientType();
				node.primaryValue = id.id();
			return node;
		}
		if (filter instanceof GroupFilter.Tag) {
			GroupFilter.Tag tag = (GroupFilter.Tag) filter;
				Node node = new Node(NodeKind.TAG);
				node.ingredientType = tag.ingredientType();
				node.primaryValue = tag.tag();
			return node;
		}
		if (filter instanceof GroupFilter.BlockTag) {
			GroupFilter.BlockTag blockTag = (GroupFilter.BlockTag) filter;
				Node node = new Node(NodeKind.BLOCK_TAG);
				node.primaryValue = blockTag.tag();
			return node;
		}
		if (filter instanceof GroupFilter.ItemPathStartsWith) {
			GroupFilter.ItemPathStartsWith startsWith = (GroupFilter.ItemPathStartsWith) filter;
				Node node = new Node(NodeKind.ITEM_PATH_STARTS_WITH);
				node.primaryValue = startsWith.prefix();
			return node;
		}
		if (filter instanceof GroupFilter.ItemPathContains) {
			GroupFilter.ItemPathContains contains = (GroupFilter.ItemPathContains) filter;
				Node node = new Node(NodeKind.ITEM_PATH_CONTAINS);
				node.primaryValue = contains.needle();
			return node;
		}
		if (filter instanceof GroupFilter.ItemPathEndsWith) {
			GroupFilter.ItemPathEndsWith endsWith = (GroupFilter.ItemPathEndsWith) filter;
				Node node = new Node(NodeKind.ITEM_PATH_ENDS_WITH);
				node.primaryValue = endsWith.suffix();
			return node;
		}
		if (filter instanceof GroupFilter.Namespace) {
			GroupFilter.Namespace namespace = (GroupFilter.Namespace) filter;
				Node node = new Node(NodeKind.NAMESPACE);
				node.ingredientType = namespace.ingredientType();
				node.primaryValue = namespace.namespace();
			return node;
		}
		if (filter instanceof GroupFilter.ExactStack) {
			GroupFilter.ExactStack exactStack = (GroupFilter.ExactStack) filter;
				Node node = new Node(NodeKind.EXACT_STACK);
				node.primaryValue = exactStack.encodedStack();
			return node;
		}
		if (filter instanceof GroupFilter.Nbt) {
			GroupFilter.Nbt nbt = (GroupFilter.Nbt) filter;
			Node node = new Node(NodeKind.NBT);
			node.primaryValue = nbt.expectedSnbt();
			return node;
		}
		if (filter instanceof GroupFilter.NbtPath) {
			GroupFilter.NbtPath nbtPath = (GroupFilter.NbtPath) filter;
			Node node = new Node(NodeKind.NBT_PATH);
			node.primaryValue = nbtPath.path();
			node.secondaryValue = nbtPath.expectedSnbt();
			return node;
		}
		if (filter instanceof GroupFilter.HasComponent) {
			GroupFilter.HasComponent hasComponent = (GroupFilter.HasComponent) filter;
				Node node = new Node(NodeKind.HAS_COMPONENT);
				node.primaryValue = hasComponent.componentTypeId();
				node.secondaryValue = hasComponent.encodedValue();
			return node;
		}
		if (filter instanceof GroupFilter.ComponentPath) {
			GroupFilter.ComponentPath componentPath = (GroupFilter.ComponentPath) filter;
				Node node = new Node(NodeKind.COMPONENT_PATH);
				node.primaryValue = componentPath.componentTypeId();
				node.secondaryValue = componentPath.path();
				node.tertiaryValue = componentPath.expectedValue();
			return node;
		}
		throw new IllegalArgumentException("Unavailable filter nodes cannot be decoded into an editable rule draft: "
			+ ((GroupFilter.Unsupported) filter).recognizedKind());
	}

	private static @Nullable GroupFilter encodeNode(Node node) {
		return switch (node.kind) {
			case ANY -> encodeCompound(node, true);
			case ALL -> encodeCompound(node, false);
			case NOT -> {
				if (node.children.size() != 1) {
					yield null;
				}
				GroupFilter child = encodeNode(node.children.get(0));
				yield child == null ? null : Filters.not(child);
			}
			case ID -> Filters.id(node.ingredientType, node.primaryValue);
			case TAG -> Filters.tag(node.ingredientType, node.primaryValue);
			case BLOCK_TAG -> Filters.blockTag(node.primaryValue);
			case ITEM_PATH_STARTS_WITH -> Filters.itemPathStartsWith(node.primaryValue);
			case ITEM_PATH_CONTAINS -> Filters.itemPathContains(node.primaryValue);
			case ITEM_PATH_ENDS_WITH -> Filters.itemPathEndsWith(node.primaryValue);
			case NAMESPACE -> Filters.namespace(node.ingredientType, node.primaryValue);
			case EXACT_STACK -> Filters.exactStack(node.primaryValue);
			case NBT -> new GroupFilter.Nbt(node.primaryValue);
			case NBT_PATH -> new GroupFilter.NbtPath(node.primaryValue, node.secondaryValue);
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
