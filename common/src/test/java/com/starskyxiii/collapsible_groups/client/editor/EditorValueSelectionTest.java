package com.starskyxiii.collapsible_groups.client.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditorValueSelectionTest {
	private EditorValuePickerKind.Snapshot ready(EditorValuePickerKind kind, String... values) {
		return new EditorValuePickerKind.Snapshot(kind, new Object(), true, List.of(values), "");
	}

	@ParameterizedTest @EnumSource(EditorValuePickerKind.class)
	void searchDoesNotInventValuesOrAutomaticallySelectRows(EditorValuePickerKind kind) {
		var selection = new EditorValueSelection();
		selection.update(ready(kind, "mekanism:clean", "c:dirty"));
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

	@ParameterizedTest @EnumSource(EditorValuePickerKind.class)
	void reloadClearsSelectionEvenWithIdenticalValues(EditorValuePickerKind kind) {
		var selection = new EditorValueSelection();
		var original = ready(kind, "c:a", "c:b");
		selection.update(original);
		selection.select(1);
		assertFalse(selection.update(original));
		assertEquals("c:b", selection.selected());
		assertTrue(selection.update(ready(kind, "c:a", "c:b")));
		assertNull(selection.selected());
		selection.select(1);
		selection.update(ready(kind, "c:a", "c:c"));
		assertNull(selection.selected());
	}

	@ParameterizedTest @EnumSource(EditorValuePickerKind.class)
	void notReadyNeverOffersOldValues(EditorValuePickerKind kind) {
		var selection = new EditorValueSelection();
		selection.update(ready(kind, "c:a"));
		selection.select(0);
		selection.update(new EditorValuePickerKind.Snapshot(kind, new Object(), false, List.of("c:stale"), ""));
		assertTrue(selection.rows().isEmpty());
		assertNull(selection.selected());
		selection.move(1);
		assertNull(selection.selected());
	}

	@ParameterizedTest @EnumSource(EditorValuePickerKind.class)
	void searchSurvivesReload(EditorValuePickerKind kind) {
		var selection = new EditorValueSelection();
		selection.search("empty");
		selection.update(ready(kind, "c:a", "c:empty"));
		assertEquals(List.of("c:empty"), selection.rows());
		selection.move(-1);
		assertEquals("c:empty", selection.selected());
	}

	@ParameterizedTest @EnumSource(EditorValuePickerKind.class)
	void navigationHandlesBoundariesAndLongIds(EditorValuePickerKind kind) {
		var selection = new EditorValueSelection();
		String longId = "c:" + "long/".repeat(150) + "value";
		selection.update(ready(kind, "c:a", longId));
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

	@Test void kindChangeClearsSelectionEvenIfTheTokenAndValuesAreIdentical() {
		var selection = new EditorValueSelection();
		Object token = new Object();
		selection.update(new EditorValuePickerKind.Snapshot(EditorValuePickerKind.TAG, token, true, List.of("c:a"), ""));
		selection.select(0);
		assertTrue(selection.update(new EditorValuePickerKind.Snapshot(EditorValuePickerKind.ID, token, true, List.of("c:a"), "")));
		assertNull(selection.selected());
	}
}
