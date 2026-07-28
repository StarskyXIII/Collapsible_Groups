package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JeiIngredientFilterHookTest {
	@Test
	void updatesDirtyStateBeforeReadingProjectedElements() {
		List<String> calls = new ArrayList<>();

		List<String> result = JeiIngredientFilterHook.getElementsAfterDirtyStateUpdate(
			() -> calls.add("dirty"),
			() -> {
				calls.add("elements");
				return List.of("projected");
			}
		);

		assertEquals(List.of("dirty", "elements"), calls);
		assertEquals(List.of("projected"), result);
	}

	@Test
	void doesNotReadProjectedElementsWhenDirtyStateUpdateFails() {
		List<String> calls = new ArrayList<>();

		assertThrows(IllegalStateException.class, () ->
			JeiIngredientFilterHook.getElementsAfterDirtyStateUpdate(
				() -> {
					calls.add("dirty");
					throw new IllegalStateException("dirty-state failure");
				},
				() -> {
					calls.add("elements");
					return List.of();
				}
			)
		);

		assertEquals(List.of("dirty"), calls);
	}
}
