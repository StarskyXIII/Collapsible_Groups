package com.starskyxiii.collapsible_groups.client.editor;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditorTagSelectionTest {
	private EditorIngredientTags ready(String... tags) {
		return new EditorIngredientTags(new Object(), "chemical", EditorIngredientTags.Status.READY,
			EditorIngredientTags.Coverage.OBSERVED_ONLY, false, List.of(tags));
	}

	@Test void searchDoesNotInventTagsOrAutomaticallySelectRows() {
		var selection = new EditorTagSelection();
		selection.update(ready("mekanism:clean", "c:dirty"));
		assertNull(selection.selected());
		selection.search("CLEAN");
		assertEquals(List.of("mekanism:clean"), selection.rows());
		assertNull(selection.selected());
		selection.move(1);
		assertEquals("mekanism:clean", selection.selected());
		selection.search("c:missing");
		assertTrue(selection.rows().isEmpty());
		assertNull(selection.selected());
	}

	@Test void reloadClearsSelectionEvenWithIdenticalTags() {
		var selection = new EditorTagSelection();
		var original = ready("c:a", "c:b");
		selection.update(original);
		selection.select(1);
		assertFalse(selection.update(original));
		assertEquals("c:b", selection.selected());
		assertTrue(selection.update(ready("c:a", "c:b")));
		assertNull(selection.selected());
		selection.select(1);
		selection.update(ready("c:a", "c:c"));
		assertNull(selection.selected());
	}

	@Test void unavailableAndPendingNeverOfferOldTags() {
		var selection = new EditorTagSelection();
		for (var status : EditorIngredientTags.Status.values()) {
			if (status == EditorIngredientTags.Status.READY) continue;
			selection.update(ready("c:a"));
			selection.select(0);
			selection.update(new EditorIngredientTags(new Object(), "chemical", status,
				EditorIngredientTags.Coverage.OBSERVED_ONLY, false, List.of("c:stale")));
			assertTrue(selection.rows().isEmpty());
			assertNull(selection.selected());
			selection.move(1);
			assertNull(selection.selected());
		}
	}

	@Test void partialResultsRemainSelectableAndSearchSurvivesReload() {
		var selection = new EditorTagSelection();
		selection.search("empty");
		selection.update(new EditorIngredientTags(new Object(), "chemical", EditorIngredientTags.Status.READY,
			EditorIngredientTags.Coverage.REGISTRY_BACKED, true, List.of("c:a", "c:empty")));
		assertEquals(List.of("c:empty"), selection.rows());
		selection.move(-1);
		assertEquals("c:empty", selection.selected());
	}

	@Test void navigationHandlesBoundariesAndLongIds() {
		var selection = new EditorTagSelection();
		String longId = "c:" + "long/".repeat(150) + "tag";
		selection.update(ready("c:a", longId));
		selection.move(-1);
		assertEquals(longId, selection.selected());
		selection.move(1);
		assertEquals(longId, selection.selected());
		selection.move(-1);
		selection.move(-1);
		assertEquals("c:a", selection.selected());
		selection.select(2);
		assertNull(selection.selected());
	}
}
