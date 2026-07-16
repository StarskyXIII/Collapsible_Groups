package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupDisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupEditorNameHelperTest {
	@Test
	void newGroupsStartWithBlankEditableName() {
		assertEquals("", GroupEditorNameHelper.initialEditName(null));
	}

	@Test
	void existingGroupsUseResolvedFallbackName() {
		GroupDefinition existing = new GroupDefinition(
			"af2_facades",
			new GroupDisplayName.Localized("collapsible_groups.group.af2_facades", "AF2: Facades"),
			true,
			Filters.itemId("minecraft:stone")
		);

		assertEquals("AF2: Facades", GroupEditorNameHelper.initialEditName(existing));
	}

	@Test
	void existingGroupsNeverStartWithAnEmptyNameField() {
		GroupDefinition existing = new GroupDefinition(
			"missing_display_name",
			new GroupDisplayName.Localized("collapsible_groups.group.missing_display_name", ""),
			true,
			Filters.itemId("minecraft:stone")
		);

		assertEquals("missing_display_name", GroupEditorNameHelper.initialEditName(existing));
	}
}
