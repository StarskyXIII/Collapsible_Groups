package com.starskyxiii.collapsible_groups.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockTagEditorDraftTest {

	@Test
	void blockTagMarksGroupAsNotStructurallyEditable() {
		GroupFilter filter = new GroupFilter.BlockTag("minecraft:logs");

		GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

		assertFalse(result.structurallyEditable());
		assertTrue(result.hasUnsupportedNodeKinds());
		assertTrue(result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.BLOCK_TAG));
	}

	@Test
	void anyContainingSupportedItemTagAndBlockTagFallsBackToEmptyUnsupportedDraft() {
		GroupFilter filter = new GroupFilter.Any(List.of(
			new GroupFilter.Tag("item", "minecraft:logs"),
			new GroupFilter.BlockTag("minecraft:mineable/axe")
		));

		GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

		assertFalse(result.structurallyEditable());
		assertTrue(result.draft().isEmpty());
		assertTrue(result.hasUnsupportedNodeKinds());
		assertTrue(result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.BLOCK_TAG));
	}

	@Test
	void blockTagUnsupportedNodeKindHasTranslationKeys() {
		GroupFilterEditorDraft.UnsupportedEditorNodeKind kind =
			GroupFilterEditorDraft.UnsupportedEditorNodeKind.BLOCK_TAG;

		assertEquals("collapsible_groups.editor.unsupported_node.block_tag.label", kind.labelKey());
		assertEquals("collapsible_groups.editor.unsupported_node.block_tag.reason", kind.reasonKey());
	}

	@Test
	void blockTagAppearsInSummaryFormatter() {
		String summary = GroupFilterSummaryFormatter.format(new GroupFilter.BlockTag("minecraft:logs"));

		assertEquals("block tag minecraft:logs", summary);
	}

	@Test
	void blockTagAppearsInClauseFormatter() {
		List<GroupFilterClauseFormatter.Clause> clauses =
			GroupFilterClauseFormatter.format(new GroupFilter.BlockTag("minecraft:logs"));

		assertEquals(1, clauses.size());
		assertEquals("Block Tag", clauses.getFirst().label());
		assertEquals("minecraft:logs", clauses.getFirst().value());
	}
}
