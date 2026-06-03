package com.starskyxiii.collapsible_groups.compat.jei.editor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorFluidSelectionHelperTest {
	@Test
	void detectsSelectedIds() {
		EditorFluidSelectionHelper helper = helper(new ArrayList<>(List.of("minecraft:water")), new AtomicInteger());

		assertTrue(helper.isIdSelected("minecraft:water"));
		assertFalse(helper.isIdSelected("minecraft:lava"));
	}

	@Test
	void toggleIdAddsAndRemovesWithOneCallbackEachTime() {
		List<String> ids = new ArrayList<>();
		AtomicInteger callbacks = new AtomicInteger();
		EditorFluidSelectionHelper helper = helper(ids, callbacks);

		helper.toggleId("minecraft:water");
		assertEquals(List.of("minecraft:water"), ids);
		assertEquals(1, callbacks.get());

		helper.toggleId("minecraft:water");
		assertEquals(List.of(), ids);
		assertEquals(2, callbacks.get());
	}

	@Test
	void addIdAppendsAndIgnoresDuplicates() {
		List<String> ids = new ArrayList<>(List.of("minecraft:water"));
		AtomicInteger callbacks = new AtomicInteger();
		EditorFluidSelectionHelper helper = helper(ids, callbacks);

		helper.addId("minecraft:lava");
		helper.addId("minecraft:water");

		assertEquals(List.of("minecraft:water", "minecraft:lava"), ids);
		assertEquals(1, callbacks.get());
	}

	@Test
	void removeIdOnlyCallsBackWhenPresent() {
		List<String> ids = new ArrayList<>(List.of("minecraft:water"));
		AtomicInteger callbacks = new AtomicInteger();
		EditorFluidSelectionHelper helper = helper(ids, callbacks);

		helper.removeId("minecraft:lava");
		assertEquals(List.of("minecraft:water"), ids);
		assertEquals(0, callbacks.get());

		helper.removeId("minecraft:water");
		assertEquals(List.of(), ids);
		assertEquals(1, callbacks.get());
	}

	@Test
	void removeIdRemovesOnlyFirstDuplicate() {
		List<String> ids = new ArrayList<>(List.of("minecraft:water", "minecraft:water"));
		AtomicInteger callbacks = new AtomicInteger();
		EditorFluidSelectionHelper helper = helper(ids, callbacks);

		helper.removeId("minecraft:water");

		assertEquals(List.of("minecraft:water"), ids);
		assertEquals(1, callbacks.get());
	}

	private static EditorFluidSelectionHelper helper(List<String> ids, AtomicInteger callbacks) {
		return new EditorFluidSelectionHelper(ids, callbacks::incrementAndGet);
	}
}
