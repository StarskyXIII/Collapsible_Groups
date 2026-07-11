package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.compat.jei.GroupUiState;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.GroupSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupManagerSearchMatcherTest {
	@Test
	void blankQueryMatchesAllFields() {
		assertTrue(GroupManagerSearchMatcher.matchesQuery("", fields("Copper Ore", "copper", "ores", GroupSource.USER, "Custom")));
		assertTrue(GroupManagerSearchMatcher.matchesQuery("   ", fields("Copper Ore", "copper", "ores", GroupSource.USER, "Custom")));
	}

	@Test
	void trimsAndLowercasesQueryWithLocaleRoot() {
		assertTrue(GroupManagerSearchMatcher.matchesQuery("  ORE  ", fields("Copper Ore", "copper", "ores", GroupSource.USER, "Custom")));
		assertTrue(GroupManagerSearchMatcher.matchesQuery("CUSTOM", fields("Copper Ore", "copper", "ores", GroupSource.USER, "Custom")));
	}

	@Test
	void queryTokensAreAndedAcrossSearchableFields() {
		GroupManagerSearchMatcher.SearchFields fields = fields("Copper Ore", "copper", "mining_pack", GroupSource.USER, "Custom");

		assertTrue(GroupManagerSearchMatcher.matchesQuery("ore custom mining", fields));
		assertFalse(GroupManagerSearchMatcher.matchesQuery("ore missing", fields));
	}

	@Test
	void tokenCanMatchAnySearchableField() {
		GroupManagerSearchMatcher.SearchFields fields = fields("金屬礦石", "Metal Ores", "metal_ores", GroupSource.BUILTIN, "內建");

		assertTrue(GroupManagerSearchMatcher.matchesQuery("金屬", fields));
		assertTrue(GroupManagerSearchMatcher.matchesQuery("Metal", fields));
		assertTrue(GroupManagerSearchMatcher.matchesQuery("metal_ores", fields));
		assertTrue(GroupManagerSearchMatcher.matchesQuery("內建", fields));
		assertTrue(GroupManagerSearchMatcher.matchesQuery("builtin", fields));
	}

	@Test
	void sourceLabelsIncludeCanonicalAndLocalizedNames() {
		assertTrue(GroupManagerSearchMatcher.matchesQuery("custom", fields("A", "A", "a", GroupSource.USER, "自訂")));
		assertTrue(GroupManagerSearchMatcher.matchesQuery("自訂", fields("A", "A", "a", GroupSource.USER, "自訂")));
		assertTrue(GroupManagerSearchMatcher.matchesQuery("built-in", fields("B", "B", "b", GroupSource.BUILTIN, "內建")));
		assertTrue(GroupManagerSearchMatcher.matchesQuery("內建", fields("B", "B", "b", GroupSource.BUILTIN, "內建")));
		assertTrue(GroupManagerSearchMatcher.matchesQuery("kubejs", fields("C", "C", "c", GroupSource.KUBEJS, "KubeJS")));
	}

	@Test
	void sourceFilterAndQueryMustBothMatch() {
		GroupManagerSearchMatcher.SearchFields fields = fields("Copper Ore", "Copper", "__default_copper", GroupSource.BUILTIN, "內建");

		assertTrue(GroupManagerSearchMatcher.matches(GroupUiState.ManagerSourceFilter.BUILTIN, "copper", fields));
		assertFalse(GroupManagerSearchMatcher.matches(GroupUiState.ManagerSourceFilter.USER, "copper", fields));
		assertFalse(GroupManagerSearchMatcher.matches(GroupUiState.ManagerSourceFilter.BUILTIN, "zinc", fields));
		assertTrue(GroupManagerSearchMatcher.matches(GroupUiState.ManagerSourceFilter.ALL, "內建", fields));
	}

	private static GroupManagerSearchMatcher.SearchFields fields(
		String resolvedDisplayName,
		String fallbackDisplayName,
		String groupId,
		GroupSource source,
		String localizedSourceLabel
	) {
		return new GroupManagerSearchMatcher.SearchFields(
			resolvedDisplayName,
			fallbackDisplayName,
			groupId,
			source,
			localizedSourceLabel
		);
	}
}
