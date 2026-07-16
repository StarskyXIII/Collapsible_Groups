package com.starskyxiii.collapsible_groups.client.editor;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless policy tests for coverage keys. This class deliberately does not load or
 * reference Minecraft classes; ItemStack integration remains in main-source code.
 */
class EditorRuleCoverageKeysTest {
	@Test
	void componentlessStackUsesBareIdWithoutConsultingExactSelectorCache() {
		AtomicInteger cacheCalls = new AtomicInteger();

		Optional<String> key = EditorRuleCoverageKeys.deriveItemKey("minecraft:potion", false, () -> {
			cacheCalls.incrementAndGet();
			return Optional.of("stack:{\"id\":\"minecraft:potion\"}");
		});

		assertEquals(Optional.of("minecraft:potion"), key);
		assertEquals(0, cacheCalls.get());
	}

	@Test
	void componentBearingStackUsesRegistryIdAndExactEncodedPayload() {
		Optional<String> key = EditorRuleCoverageKeys.deriveItemKey("minecraft:potion", true,
			() -> Optional.of("stack:{\"id\":\"minecraft:potion\",\"components\":{\"minecraft:potion_contents\":{}}}"));

		assertEquals(Optional.of("minecraft:potion#{\"id\":\"minecraft:potion\",\"components\":{\"minecraft:potion_contents\":{}}}"), key);
	}

	@Test
	void encodingFailureIsUnkeyableAndCannotMatchBareIdCoverage() {
		Optional<String> key = EditorRuleCoverageKeys.deriveItemKey("minecraft:potion", true, Optional::empty);

		assertTrue(key.isEmpty());
		assertFalse(key.map(java.util.Set.of("minecraft:potion")::contains).orElse(false));
	}

	@Test
	void semanticallySameComponentsProduceSameKey() {
		String encoded = "stack:{\"id\":\"minecraft:potion\",\"components\":{\"minecraft:potion_contents\":{\"potion\":\"minecraft:healing\"}}}";

		assertEquals(
			EditorRuleCoverageKeys.deriveItemKey("minecraft:potion", true, () -> Optional.of(encoded)),
			EditorRuleCoverageKeys.deriveItemKey("minecraft:potion", true, () -> Optional.of(encoded)));
	}

	@Test
	void differentComponentsProduceDifferentKeys() {
		Optional<String> healing = EditorRuleCoverageKeys.deriveItemKey("minecraft:potion", true,
			() -> Optional.of("stack:{\"id\":\"minecraft:potion\",\"components\":{\"minecraft:potion_contents\":{\"potion\":\"minecraft:healing\"}}}"));
		Optional<String> swiftness = EditorRuleCoverageKeys.deriveItemKey("minecraft:potion", true,
			() -> Optional.of("stack:{\"id\":\"minecraft:potion\",\"components\":{\"minecraft:potion_contents\":{\"potion\":\"minecraft:swiftness\"}}}"));

		assertNotEquals(healing, swiftness);
	}
}
