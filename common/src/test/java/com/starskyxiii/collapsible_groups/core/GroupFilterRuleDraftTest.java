package com.starskyxiii.collapsible_groups.core;

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
}
