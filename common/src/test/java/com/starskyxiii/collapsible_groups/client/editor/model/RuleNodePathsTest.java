package com.starskyxiii.collapsible_groups.client.editor.model;

import com.starskyxiii.collapsible_groups.client.editor.model.RuleNodePaths;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleNodePathsTest {
	@Test
	void pathsFollowChildIndexesFromRoot() {
		GroupFilterRuleDraft tree = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = tree.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node tag = tree.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.TAG);
		GroupFilterRuleDraft.Node any = tree.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ANY);
		GroupFilterRuleDraft.Node not = tree.insertRelativeTo(any, GroupFilterRuleDraft.NodeKind.NOT);
		GroupFilterRuleDraft.Node id = tree.insertRelativeTo(not, GroupFilterRuleDraft.NodeKind.ID);

		assertEquals("", RuleNodePaths.pathOf(root));
		assertEquals("0", RuleNodePaths.pathOf(tag));
		assertEquals("1", RuleNodePaths.pathOf(any));
		assertEquals("1.0", RuleNodePaths.pathOf(not));
		assertEquals("1.0.0", RuleNodePaths.pathOf(id));
		assertEquals("", RuleNodePaths.pathOf(null));
	}

	@Test
	void appendingSiblingsKeepsEarlierPathsStable() {
		GroupFilterRuleDraft tree = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = tree.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node any = tree.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ANY);
		String before = RuleNodePaths.pathOf(any);

		tree.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.TAG);

		assertEquals(before, RuleNodePaths.pathOf(any));
	}

	@Test
	void detachedNodesFallBackToEmptyPath() {
		GroupFilterRuleDraft tree = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = tree.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node tag = tree.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.TAG);
		tree.delete(tag);

		assertEquals("", RuleNodePaths.pathOf(tag));
	}
}
