package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.widget.EditorShellLayout;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroupEditorTooltipHelperTest {
	private static final EditorShellLayout.Rect SEARCH = new EditorShellLayout.Rect(10, 20, 80, 12);

	@Test
	void searchSyntaxTooltipRequiresVisibleSearchFieldAndRectangleHit() {
		assertTrue(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(true, SEARCH, 10, 20));
		assertFalse(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(false, SEARCH, 10, 20));
		assertFalse(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(true, SEARCH, 9, 20));
		assertFalse(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(true, SEARCH, 90, 20));
	}

	@Test
	void tooltipOwnershipCopyCanAppendWithoutMutatingImmutableProviderSnapshot() {
		List<Component> snapshot = List.of(Component.literal("provider"));

		List<Component> working = GroupEditorTooltipHelper.ownTooltipLines(snapshot);
		working.add(Component.literal("editor hint"));

		assertNotSame(snapshot, working);
		assertEquals(1, snapshot.size());
		assertEquals(2, working.size());
	}
}
