package com.starskyxiii.collapsible_groups.client.manager.model;

import com.starskyxiii.collapsible_groups.client.manager.model.SavedGroupContext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedGroupContextTest {
	@Test
	void onlyCreatedAndCopiedGroupsRequestReveal() {
		assertTrue(new SavedGroupContext("created", SavedGroupContext.SaveKind.CREATED).shouldReveal());
		assertTrue(new SavedGroupContext("copied", SavedGroupContext.SaveKind.COPIED).shouldReveal());
		assertFalse(new SavedGroupContext("updated", SavedGroupContext.SaveKind.UPDATED).shouldReveal());
	}
}
