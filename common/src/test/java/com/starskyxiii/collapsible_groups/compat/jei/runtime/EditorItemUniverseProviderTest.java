package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorItemUniverseProviderTest {
	@Test
	void keepsPreferredVariantUniverseWithoutCallingFallback() {
		AtomicBoolean fallbackCalled = new AtomicBoolean();
		List<String> result = EditorItemUniverseProvider.preferNonEmpty(
			List.of("potion-a", "potion-b"),
			() -> {
				fallbackCalled.set(true);
				return List.of("default");
			});

		assertEquals(List.of("potion-a", "potion-b"), result);
		assertFalse(fallbackCalled.get());
	}

	@Test
	void emptyPreferredUniverseUsesFallback() {
		AtomicBoolean fallbackCalled = new AtomicBoolean();
		List<String> result = EditorItemUniverseProvider.preferNonEmpty(List.of(), () -> {
			fallbackCalled.set(true);
			return List.of("default-a", "default-b");
		});

		assertEquals(List.of("default-a", "default-b"), result);
		assertTrue(fallbackCalled.get());
	}
}
