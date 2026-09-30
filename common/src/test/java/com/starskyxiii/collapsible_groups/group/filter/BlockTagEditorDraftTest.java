package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterClauseFormatter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterSummaryFormatter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockTagEditorDraftTest {

	@Test
	void blockTagRootIsPreservedAndEditable() {
		// a non-Any, non-supported-leaf root is preserved whole and stays editable
		// (auto-wrap on first edit). It is not flat-index safe.
		GroupFilter filter = new GroupFilter.BlockTag("minecraft:logs");

		GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

		assertTrue(result.structurallyEditable());
		assertFalse(result.flatIndexSafe());
		assertTrue(result.draft().isEmpty());
		assertEquals(List.of(filter), result.preservedSubtrees());
		assertTrue(result.hasUnsupportedNodeKinds());
		assertTrue(result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.BLOCK_TAG));
	}

	@Test
	void anyContainingSupportedItemTagAndBlockTagIsHybridEditable() {
		// root Any splits into flat item tag (contents) + preserved block tag.
		GroupFilter.Tag itemTag = new GroupFilter.Tag("item", "minecraft:logs");
		GroupFilter.BlockTag blockTag = new GroupFilter.BlockTag("minecraft:mineable/axe");
		GroupFilter filter = new GroupFilter.Any(List.of(itemTag, blockTag));

		GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

		assertTrue(result.structurallyEditable());
		assertFalse(result.flatIndexSafe());
		assertFalse(result.draft().isEmpty());
		assertEquals(List.of("minecraft:logs"), result.draft().itemTags());
		assertEquals(List.of(blockTag), result.preservedSubtrees());
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
		assertEquals("Block Tag", clauses.get(0).label());
		assertEquals("minecraft:logs", clauses.get(0).value());
	}
}
