package com.starskyxiii.collapsible_groups.client.editor.model;

import com.starskyxiii.collapsible_groups.client.editor.model.RuleTagResolution;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleTagResolutionTest {
	private static final RuleTagResolution.TagExistenceLookup ONLY_EXISTS =
		(registry, tagId) -> tagId.getPath().equals("exists");

	private final GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();

	private GroupFilterRuleDraft.Node node(GroupFilterRuleDraft.NodeKind kind, String type, String value) {
		GroupFilterRuleDraft.Node node = draft.createNode(kind);
		node.setIngredientType(type);
		node.setPrimaryValue(value);
		return node;
	}

	@Test
	void tagRegistrySelectionFollowsKindAndIngredientType() {
		assertEquals(RuleTagResolution.TagRegistryKind.ITEM,
			RuleTagResolution.tagRegistryFor(node(GroupFilterRuleDraft.NodeKind.TAG, "item", "c:x")));
		assertEquals(RuleTagResolution.TagRegistryKind.FLUID,
			RuleTagResolution.tagRegistryFor(node(GroupFilterRuleDraft.NodeKind.TAG, "Fluid", "c:x")));
		assertEquals(RuleTagResolution.TagRegistryKind.BLOCK,
			RuleTagResolution.tagRegistryFor(node(GroupFilterRuleDraft.NodeKind.BLOCK_TAG, "item", "c:x")));
		assertNull(RuleTagResolution.tagRegistryFor(node(GroupFilterRuleDraft.NodeKind.TAG, "entity", "c:x")));
		assertNull(RuleTagResolution.tagRegistryFor(node(GroupFilterRuleDraft.NodeKind.ID, "item", "c:x")));
		assertNull(RuleTagResolution.tagRegistryFor(node(GroupFilterRuleDraft.NodeKind.NAMESPACE, "item", "c")));
	}

	@Test
	void unresolvedMeansParseableButAbsentFromRegistry() {
		assertFalse(RuleTagResolution.isUnresolved(
			node(GroupFilterRuleDraft.NodeKind.TAG, "item", "c:exists"), ONLY_EXISTS));
		assertTrue(RuleTagResolution.isUnresolved(
			node(GroupFilterRuleDraft.NodeKind.TAG, "item", "c:missing"), ONLY_EXISTS));
		assertTrue(RuleTagResolution.isUnresolved(
			node(GroupFilterRuleDraft.NodeKind.BLOCK_TAG, "item", "c:missing"), ONLY_EXISTS));
	}

	@Test
	void syntaxAxisStaysOutOfUnresolvedDetection() {
		assertFalse(RuleTagResolution.isUnresolved(
			node(GroupFilterRuleDraft.NodeKind.TAG, "item", ""), ONLY_EXISTS));
		assertFalse(RuleTagResolution.isUnresolved(
			node(GroupFilterRuleDraft.NodeKind.TAG, "item", "c:UPPER CASE!!"), ONLY_EXISTS));
		assertFalse(RuleTagResolution.isUnresolved(
			node(GroupFilterRuleDraft.NodeKind.TAG, "entity", "c:missing"), ONLY_EXISTS));
		assertFalse(RuleTagResolution.isUnresolved(
			node(GroupFilterRuleDraft.NodeKind.ITEM_PATH_CONTAINS, "item", "c:missing"), ONLY_EXISTS));
	}

	@Test
	void countWalksFlattenedNodes() {
		GroupFilterRuleDraft tree = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = tree.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		GroupFilterRuleDraft.Node ok = tree.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.TAG);
		ok.setPrimaryValue("c:exists");
		GroupFilterRuleDraft.Node missing = tree.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.TAG);
		missing.setPrimaryValue("c:missing");
		GroupFilterRuleDraft.Node nested = tree.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.NOT);
		GroupFilterRuleDraft.Node nestedMissing = tree.insertRelativeTo(nested, GroupFilterRuleDraft.NodeKind.BLOCK_TAG);
		nestedMissing.setPrimaryValue("c:missing_block");

		assertEquals(2, RuleTagResolution.countUnresolved(tree.flatten(), ONLY_EXISTS));
	}
}
