package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchUngroupPolicyTest {
	@Test
	void ungroupsOnlyWhenEnabledAndSearchIsActive() {
		assertTrue(SearchUngroupPolicy.shouldUngroup(true, true, 2, 5));
		assertFalse(SearchUngroupPolicy.shouldUngroup(false, true, 2, 5));
		assertFalse(SearchUngroupPolicy.shouldUngroup(true, false, 2, 5));
	}

	@Test
	void thresholdUsesStrictlyLowerThan() {
		assertTrue(SearchUngroupPolicy.shouldUngroup(true, true, 4, 5));
		assertFalse(SearchUngroupPolicy.shouldUngroup(true, true, 5, 5));
		assertFalse(SearchUngroupPolicy.shouldUngroup(true, true, 2, 2));
	}

	@Test
	void nonPositiveThresholdDisablesUngrouping() {
		assertFalse(SearchUngroupPolicy.shouldUngroup(true, true, 2, 0));
		assertFalse(SearchUngroupPolicy.shouldUngroup(true, true, 2, -1));
	}

	@Test
	void singletonResultsAreAlreadyUngroupedByExistingBehavior() {
		assertFalse(SearchUngroupPolicy.shouldUngroup(true, true, 1, 5));
	}
}
