package com.starskyxiii.collapsible_groups.client.editor;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditorTypeSelectionTest {
	@Test void searchFindsAliasesWithoutChangingTheStoredIdOrChoosingFirstRow() {
		var model = new EditorTypeSelection();
		model.update(catalog("Mekanism.ChemicalStack", "emi:other"));
		assertNull(model.selected());
		model.search("GAS");
		assertEquals(1, model.rows().size());
		assertNull(model.selected());
		model.move(1);
		assertEquals("Mekanism.ChemicalStack", model.selected());
		model.search("missing");
		assertNull(model.selected());
		assertTrue(model.rows().isEmpty());
	}

	@Test void reloadInvalidatesSelectionEvenWhenAnIdenticalRowPositionSurvives() {
		var model = new EditorTypeSelection();
		var first = catalog("A");
		model.update(first);
		model.select(0);
		assertFalse(model.update(first));
		assertEquals("A", model.selected());
		assertTrue(model.update(catalog("B")));
		assertNull(model.selected());
		model.select(0);
		model.update(EditorIngredientTypes.PENDING);
		assertNull(model.selected());
		assertTrue(model.rows().isEmpty());
		model.update(catalog("B"));
		assertNull(model.selected());
	}

	@Test void keyboardNavigationHandlesEmptySingleAndBoundaries() {
		var model = new EditorTypeSelection();
		model.move(-1);
		assertNull(model.selected());
		model.update(catalog("A", "B"));
		model.move(-1);
		assertEquals("B", model.selected());
		model.move(1);
		assertEquals("B", model.selected());
		model.move(-1);
		assertEquals("A", model.selected());
		model.select(5);
		assertNull(model.selected());
		model.update(catalog("A"));
		assertNull(model.selected());
		model.move(1);
		assertEquals("A", model.selected());
	}

	private static EditorIngredientTypes catalog(String... ids) {
		return new EditorIngredientTypes(new Object(), EditorIngredientTypes.Status.READY,
			java.util.Arrays.stream(ids).map(id -> new EditorIngredientTypes.Option(id,
				id.startsWith("Mekanism") ? List.of("gas") : List.of())).toList());
	}
}
