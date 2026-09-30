package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterClauseFormatter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterSummaryFormatter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPathEditorDraftTest {

	@Test
	void itemPathStartsWithRootIsPreservedAndEditable() {
		// preserved whole, editable, not flat-index safe.
		GroupFilter filter = new GroupFilter.ItemPathStartsWith("gutter_");

		GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

		assertTrue(result.structurallyEditable());
		assertFalse(result.flatIndexSafe());
		assertEquals(List.of(filter), result.preservedSubtrees());
		assertTrue(result.hasUnsupportedNodeKinds());
		assertTrue(result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.ITEM_PATH_STARTS_WITH));
	}

	@Test
	void anyContainingSupportedItemTagAndItemPathEndsWithIsHybridEditable() {
		GroupFilter.Tag itemTag = new GroupFilter.Tag("item", "minecraft:planks");
		GroupFilter.ItemPathEndsWith endsWith = new GroupFilter.ItemPathEndsWith("_chair");
		GroupFilter filter = new GroupFilter.Any(List.of(itemTag, endsWith));

		GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

		assertTrue(result.structurallyEditable());
		assertFalse(result.flatIndexSafe());
		assertFalse(result.draft().isEmpty());
		assertEquals(List.of("minecraft:planks"), result.draft().itemTags());
		assertEquals(List.of(endsWith), result.preservedSubtrees());
		assertTrue(result.hasUnsupportedNodeKinds());
		assertTrue(result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.ITEM_PATH_ENDS_WITH));
	}

	@Test
	void itemPathContainsRootIsPreservedAndEditable() {
		GroupFilter filter = new GroupFilter.ItemPathContains("_beam_");

		GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

		assertTrue(result.structurallyEditable());
		assertFalse(result.flatIndexSafe());
		assertEquals(List.of(filter), result.preservedSubtrees());
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
		assertEquals("Item Path Starts With", startsWithClauses.get(0).label());
		assertEquals("gutter_", startsWithClauses.get(0).value());

		assertEquals(1, endsWithClauses.size());
		assertEquals("Item Path Ends With", endsWithClauses.get(0).label());
		assertEquals("_chair", endsWithClauses.get(0).value());

		assertEquals(1, containsClauses.size());
		assertEquals("Item Path Contains", containsClauses.get(0).label());
		assertEquals("_beam_", containsClauses.get(0).value());
	}
}
