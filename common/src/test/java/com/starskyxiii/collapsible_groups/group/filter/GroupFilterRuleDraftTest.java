package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GroupFilterRuleDraftTest {
	@Test
	void decodeAndEncodePreservesNestedStructure() {
		GroupFilter original = new GroupFilter.All(java.util.List.of(
			new GroupFilter.Namespace("item", "minecraft"),
			new GroupFilter.Not(new GroupFilter.ComponentPath(
				"irons_spellbooks:spell_container",
				"data[0].id",
				"irons_spellbooks:blood_needles"
			))
		));

		GroupFilterRuleDraft draft = GroupFilterRuleDraft.decode(original);

		assertTrue(draft.hasRoot());
		assertTrue(draft.toFilter().isPresent());
		assertEquals(original, draft.toFilter().get());
	}

	@Test
	void rootLeafCanBeWrappedEvenWhenRelativeInsertIsUnavailable() {
		for (GroupFilterRuleDraft.NodeKind wrapperKind : java.util.List.of(
			GroupFilterRuleDraft.NodeKind.ANY,
			GroupFilterRuleDraft.NodeKind.ALL,
			GroupFilterRuleDraft.NodeKind.NOT
		)) {
			GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
			GroupFilterRuleDraft.Node root = draft.insertRelativeTo(null, GroupFilterRuleDraft.NodeKind.ID);

			assertNotNull(root);
			assertFalse(draft.canInsertRelativeTo(root));
			assertTrue(draft.canWrap(root, wrapperKind));

			GroupFilterRuleDraft.Node wrapper = draft.wrap(root, wrapperKind);

			assertNotNull(wrapper);
			assertSame(wrapper, draft.root());
			assertSame(wrapper, root.parent());
			assertEquals(java.util.List.of(root), wrapper.children());
		}
	}

	@Test
	void insertWrapAndDeleteMutateTreeAsExpected() {
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = draft.insertRelativeTo(null, GroupFilterRuleDraft.NodeKind.ID);
		assertNotNull(root);
		root.setIngredientType("item");
		root.setPrimaryValue("minecraft:stone");

		GroupFilterRuleDraft.Node wrapper = draft.wrap(root, GroupFilterRuleDraft.NodeKind.NOT);
		assertNotNull(wrapper);
		assertEquals(GroupFilterRuleDraft.NodeKind.NOT, wrapper.kind());
		assertSame(wrapper, draft.root());

		GroupFilterRuleDraft.Node sibling = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.TAG);
		assertNull(sibling, "NOT should not allow adding sibling/child nodes past its single slot");

		GroupFilterRuleDraft.Node newRoot = draft.delete(wrapper);
		assertNull(newRoot);
		assertFalse(draft.hasRoot());
	}

	@Test
	void emptyCompoundNodesStayAsIncompleteDrafts() {
		GroupFilterRuleDraft allDraft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node allRoot = allDraft.insertRelativeTo(null, GroupFilterRuleDraft.NodeKind.ALL);
		assertNotNull(allRoot);
		assertTrue(allDraft.hasRoot());
		assertTrue(allDraft.toFilter().isEmpty(), "Empty ALL node should not crash or encode");

		GroupFilterRuleDraft anyDraft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node anyRoot = anyDraft.insertRelativeTo(null, GroupFilterRuleDraft.NodeKind.ANY);
		assertNotNull(anyRoot);
		assertTrue(anyDraft.hasRoot());
		assertTrue(anyDraft.toFilter().isEmpty(), "Empty ANY node should not crash or encode");
	}

	@Test
	void moveNodeReparentsLeafIntoAnotherCompound() {
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = draft.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node any = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ANY);
		GroupFilterRuleDraft.Node leaf = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ID);
		assertNotNull(any);
		assertNotNull(leaf);
		assertSame(root, leaf.parent());

		assertTrue(draft.canMove(leaf, any));
		assertTrue(draft.moveNode(leaf, any, any.children().size()));

		assertSame(any, leaf.parent());
		assertFalse(root.children().contains(leaf));
		assertEquals(java.util.List.of(leaf), any.children());
	}

	@Test
	void moveNodeRejectsCycleIntoOwnSubtree() {
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = draft.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node outer = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ANY);
		GroupFilterRuleDraft.Node inner = draft.insertRelativeTo(outer, GroupFilterRuleDraft.NodeKind.ALL);
		assertNotNull(outer);
		assertNotNull(inner);

		// Descendant checks.
		assertTrue(GroupFilterRuleDraft.isDescendantOf(inner, outer));
		assertTrue(GroupFilterRuleDraft.isDescendantOf(outer, outer));
		assertFalse(GroupFilterRuleDraft.isDescendantOf(outer, inner));

		// Moving outer into its own descendant would create a cycle.
		assertFalse(draft.canMove(outer, inner));
		assertFalse(draft.moveNode(outer, inner, 0));
		// Moving into itself is rejected.
		assertFalse(draft.canMove(outer, outer));
	}

	@Test
	void moveNodeHonoursCompoundConstraints() {
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = draft.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node not = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.NOT);
		GroupFilterRuleDraft.Node filled = draft.insertRelativeTo(not, GroupFilterRuleDraft.NodeKind.ID);
		GroupFilterRuleDraft.Node leaf = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.TAG);
		assertNotNull(not);
		assertNotNull(filled);
		assertNotNull(leaf);

		// NOT already at maxChildren=1 → canAcceptChild() false → rejected.
		assertFalse(draft.canMove(leaf, not));
		// A leaf target is not compound → rejected.
		assertFalse(draft.canMove(leaf, filled));
		// The root itself cannot be moved.
		assertFalse(draft.canMove(root, not));
	}

	@Test
	void moveNodeClampsIndexAfterDetachWithinSameParent() {
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = draft.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node a = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ID);
		GroupFilterRuleDraft.Node b = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.TAG);
		GroupFilterRuleDraft.Node c = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.NAMESPACE);
		assertEquals(java.util.List.of(a, b, c), root.children());

		// Move a to the tail of its own parent using an index equal to the pre-detach
		// size (3). After detach the list has size 2, so the clamp lands it at index 2.
		assertTrue(draft.moveNode(a, root, root.children().size()));
		assertEquals(java.util.List.of(b, c, a), root.children());
		assertSame(root, a.parent());
	}

	@Test
	void moveNodeSameParentDownUsesPreDetachIndex() {
		// Pre-detach coordinate space: move a (index 0) to visual gap index 3 (== end).
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = draft.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node a = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ID);
		GroupFilterRuleDraft.Node b = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.TAG);
		GroupFilterRuleDraft.Node c = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.NAMESPACE);
		assertEquals(java.util.List.of(a, b, c), root.children());

		// Insert a "before c" in pre-detach terms → index 2. oldIndex 0 < 2 so it decrements
		// to 1 post-detach, landing a between b and c.
		assertTrue(draft.moveNode(a, root, 2));
		assertEquals(java.util.List.of(b, a, c), root.children());
	}

	@Test
	void moveNodeSameParentUpKeepsPreDetachIndex() {
		// Move c (index 2) to the front (pre-detach index 0). oldIndex 2 is not < 0 so no
		// decrement; clamp keeps 0.
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = draft.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node a = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ID);
		GroupFilterRuleDraft.Node b = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.TAG);
		GroupFilterRuleDraft.Node c = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.NAMESPACE);
		assertEquals(java.util.List.of(a, b, c), root.children());

		assertTrue(draft.moveNode(c, root, 0));
		assertEquals(java.util.List.of(c, a, b), root.children());
	}

	@Test
	void moveNodeCrossParentInsertsAtPreDetachIndex() {
		// Cross-parent: the same-parent decrement must NOT fire, so the caller index is used
		// verbatim (clamped only).
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = draft.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node any = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ANY);
		GroupFilterRuleDraft.Node x = draft.insertRelativeTo(any, GroupFilterRuleDraft.NodeKind.ID);
		GroupFilterRuleDraft.Node y = draft.insertRelativeTo(any, GroupFilterRuleDraft.NodeKind.TAG);
		GroupFilterRuleDraft.Node leaf = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.NAMESPACE);
		assertNotNull(leaf);
		assertEquals(java.util.List.of(x, y), any.children());

		// Insert leaf between x and y (index 1). Different parent → no decrement.
		assertTrue(draft.moveNode(leaf, any, 1));
		assertEquals(java.util.List.of(x, leaf, y), any.children());
		assertSame(any, leaf.parent());
		assertFalse(root.children().contains(leaf));
	}

	@Test
	void moveNodeCrossParentTailClampsWithoutDecrement() {
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = draft.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node any = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ANY);
		GroupFilterRuleDraft.Node x = draft.insertRelativeTo(any, GroupFilterRuleDraft.NodeKind.ID);
		GroupFilterRuleDraft.Node leaf = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.NAMESPACE);
		assertNotNull(leaf);

		// Tail insert via children().size() (== 1). Cross-parent so clamp caps at new size 1.
		assertTrue(draft.moveNode(leaf, any, any.children().size()));
		assertEquals(java.util.List.of(x, leaf), any.children());
	}

	@Test
	void canMoveAllowsReorderingTheLoneChildOfNot() {
		// Same-parent capacity exemption: a NOT is at maxChildren but its own child may still be
		// reordered within it (no-op geometrically, but must not be flatly rejected).
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node not = draft.setRoot(GroupFilterRuleDraft.NodeKind.NOT);
		GroupFilterRuleDraft.Node child = draft.insertRelativeTo(not, GroupFilterRuleDraft.NodeKind.ID);
		assertNotNull(child);
		assertFalse(not.canAcceptChild());

		assertTrue(draft.canMove(child, not), "the NOT's own child must remain reorderable within it");
		assertTrue(draft.moveNode(child, not, 0));
		assertSame(not, child.parent());
	}

	@Test
	void decodeAndEncodePreservesItemPathContains() {
		GroupFilter original = new GroupFilter.ItemPathContains("_beam_");

		GroupFilterRuleDraft draft = GroupFilterRuleDraft.decode(original);

		assertTrue(draft.hasRoot());
		assertEquals(GroupFilterRuleDraft.NodeKind.ITEM_PATH_CONTAINS, draft.root().kind());
		assertEquals("_beam_", draft.root().primaryValue());
		assertTrue(draft.toFilter().isPresent());
		assertEquals(original, draft.toFilter().get());
	}
}
