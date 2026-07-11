package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.core.GroupSortMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupManagerSortTest {
	private record Entry(String id, String name) {}

	@Test
	void priorityPreservesPublishedOrder() {
		List<Entry> entries = List.of(new Entry("b", "Beta"), new Entry("a", "Alpha"));
		assertEquals(entries, GroupManagerSort.apply(entries, GroupSortMode.PRIORITY, Entry::name, Entry::id));
	}

	@Test
	void nameModesNormalizeAndKeepIdTieBreakAscending() {
		List<Entry> entries = List.of(
			new Entry("z", "Ｂeta"), new Entry("b", "alpha"), new Entry("a", "ALPHA"));

		assertEquals(List.of("a", "b", "z"), ids(GroupManagerSort.apply(
			entries, GroupSortMode.NAME_ASC, Entry::name, Entry::id)));
		assertEquals(List.of("z", "a", "b"), ids(GroupManagerSort.apply(
			entries, GroupSortMode.NAME_DESC, Entry::name, Entry::id)));
	}

	private static List<String> ids(List<Entry> entries) {
		return entries.stream().map(Entry::id).toList();
	}
}
