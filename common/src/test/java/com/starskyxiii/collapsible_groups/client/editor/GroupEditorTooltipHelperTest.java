package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.widget.EditorShellLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupEditorTooltipHelperTest {
	private static final EditorShellLayout.Rect SEARCH = new EditorShellLayout.Rect(10, 20, 80, 12);

	@Test
	void searchSyntaxTooltipRequiresVisibleSearchFieldAndRectangleHit() {
		assertTrue(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(true, SEARCH, 10, 20));
		assertFalse(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(false, SEARCH, 10, 20));
		assertFalse(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(true, SEARCH, 9, 20));
		assertFalse(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(true, SEARCH, 90, 20));
	}
}
