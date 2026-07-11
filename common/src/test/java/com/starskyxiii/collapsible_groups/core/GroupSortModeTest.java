package com.starskyxiii.collapsible_groups.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupSortModeTest {
	@Test
	void unknownAndMissingIdsFallBackToPriority() {
		assertEquals(GroupSortMode.PRIORITY, GroupSortMode.fromId(null));
		assertEquals(GroupSortMode.PRIORITY, GroupSortMode.fromId("unknown"));
		assertEquals(GroupSortMode.NAME_ASC, GroupSortMode.fromId("name_asc"));
		assertEquals(GroupSortMode.NAME_DESC, GroupSortMode.fromId("name_desc"));
	}
}
