package com.starskyxiii.collapsible_groups.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPathEditorDraftTest {

	@Test
	void itemPathStartsWithMarksGroupAsNotStructurallyEditable() {
		GroupFilter filter = new GroupFilter.ItemPathStartsWith("gutter_");

		GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

		assertFalse(result.structurallyEditable());
		assertTrue(result.hasUnsupportedNodeKinds());
		assertTrue(result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.ITEM_PATH_STARTS_WITH));
	}

	@Test
	void anyContainingSupportedItemTagAndItemPathEndsWithFallsBackToEmptyUnsupportedDraft() {
		GroupFilter filter = new GroupFilter.Any(List.of(
			new GroupFilter.Tag("item", "minecraft:planks"),
			new GroupFilter.ItemPathEndsWith("_chair")
		));

		GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

		assertFalse(result.structurallyEditable());
		assertTrue(result.draft().isEmpty());
		assertTrue(result.hasUnsupportedNodeKinds());
		assertTrue(result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.ITEM_PATH_ENDS_WITH));
	}

	@Test
	void itemPathContainsMarksGroupAsNotStructurallyEditable() {
		GroupFilter filter = new GroupFilter.ItemPathContains("_beam_");

		GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

		assertFalse(result.structurallyEditable());
		assertTrue(result.hasUnsupportedNodeKinds());
		assertTrue(result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.ITEM_PATH_CONTAINS));
	}

	@Test
	void itemPathUnsupportedNodeKindsHaveTranslationKeys() {
		assertEquals(
			"collapsible_groups.editor.unsupported_node.item_path_starts_with.label",
			GroupFilterEditorDraft.UnsupportedEditorNodeKind.ITEM_PATH_STARTS_WITH.labelKey()
		);
		assertEquals(
			"collapsible_groups.editor.unsupported_node.item_path_starts_with.reason",
			GroupFilterEditorDraft.UnsupportedEditorNodeKind.ITEM_PATH_STARTS_WITH.reasonKey()
		);
		assertEquals(
			"collapsible_groups.editor.unsupported_node.item_path_ends_with.label",
			GroupFilterEditorDraft.UnsupportedEditorNodeKind.ITEM_PATH_ENDS_WITH.labelKey()
		);
		assertEquals(
			"collapsible_groups.editor.unsupported_node.item_path_ends_with.reason",
			GroupFilterEditorDraft.UnsupportedEditorNodeKind.ITEM_PATH_ENDS_WITH.reasonKey()
		);
		assertEquals(
			"collapsible_groups.editor.unsupported_node.item_path_contains.label",
			GroupFilterEditorDraft.UnsupportedEditorNodeKind.ITEM_PATH_CONTAINS.labelKey()
		);
		assertEquals(
			"collapsible_groups.editor.unsupported_node.item_path_contains.reason",
			GroupFilterEditorDraft.UnsupportedEditorNodeKind.ITEM_PATH_CONTAINS.reasonKey()
		);
	}

	@Test
	void itemPathAppearsInSummaryFormatter() {
		assertEquals(
			"item path starts with gutter_",
			GroupFilterSummaryFormatter.format(new GroupFilter.ItemPathStartsWith("gutter_"))
		);
		assertEquals(
			"item path ends with _chair",
			GroupFilterSummaryFormatter.format(new GroupFilter.ItemPathEndsWith("_chair"))
		);
		assertEquals(
			"item path contains _beam_",
			GroupFilterSummaryFormatter.format(new GroupFilter.ItemPathContains("_beam_"))
		);
	}

	@Test
	void itemPathAppearsInClauseFormatter() {
		List<GroupFilterClauseFormatter.Clause> startsWithClauses =
			GroupFilterClauseFormatter.format(new GroupFilter.ItemPathStartsWith("gutter_"));
		List<GroupFilterClauseFormatter.Clause> endsWithClauses =
			GroupFilterClauseFormatter.format(new GroupFilter.ItemPathEndsWith("_chair"));
		List<GroupFilterClauseFormatter.Clause> containsClauses =
			GroupFilterClauseFormatter.format(new GroupFilter.ItemPathContains("_beam_"));

		assertEquals(1, startsWithClauses.size());
		assertEquals("Item Path Starts With", startsWithClauses.getFirst().label());
		assertEquals("gutter_", startsWithClauses.getFirst().value());

		assertEquals(1, endsWithClauses.size());
		assertEquals("Item Path Ends With", endsWithClauses.getFirst().label());
		assertEquals("_chair", endsWithClauses.getFirst().value());

		assertEquals(1, containsClauses.size());
		assertEquals("Item Path Contains", containsClauses.getFirst().label());
		assertEquals("_beam_", containsClauses.getFirst().value());
	}
}
