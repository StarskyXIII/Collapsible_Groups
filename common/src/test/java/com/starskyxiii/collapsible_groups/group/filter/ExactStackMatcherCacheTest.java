package com.starskyxiii.collapsible_groups.group.filter;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactStackMatcherCacheTest {
	private static final ResourceLocation FACADE = ResourceLocation.parse("ae2:facade");
	private static final ResourceLocation OTHER = ResourceLocation.parse("minecraft:stone");

	@Test void singletonDecodesOnceAcrossTenThousandCandidates() {
		AtomicInteger attempts = new AtomicInteger();
		AtomicInteger decodes = new AtomicInteger();
		var cache = new ExactStackMatcherCache<>(List.of("facade-with-components"), () -> {
			attempts.incrementAndGet();
			return attempt(true, encoded -> {
				decodes.incrementAndGet();
				return Optional.of(decoded(FACADE, componentValue("red", true)));
			});
		});

		for (int i = 0; i < 10_000; i++) {
			assertTrue(cache.matches(FACADE, componentValue("red", true)::equals));
		}
		assertEquals(1, attempts.get());
		assertEquals(1, decodes.get());
	}

	@Test void itemIdGateSkipsDeepComparisonForOtherItems() {
		AtomicInteger comparisons = new AtomicInteger();
		var cache = liveCache(decoded(FACADE, componentValue("red", true)));
		assertFalse(cache.matches(OTHER, value -> {
			comparisons.incrementAndGet();
			return true;
		}));
		assertEquals(0, comparisons.get());
	}

	@Test void componentDeepEqualityDistinguishesNestedValues() {
		var reference = componentValue("red", true);
		var cache = liveCache(decoded(FACADE, reference));
		assertFalse(cache.matches(FACADE, componentValue("blue", true)::equals));
		assertFalse(cache.matches(FACADE, componentValue("red", false)::equals));
		assertTrue(cache.matches(FACADE, componentValue("red", true)::equals));
	}

	@Test void allFailedFallbackAttemptRetriesWhenLiveRegistryArrives() {
		AtomicInteger attempts = new AtomicInteger();
		var cache = new ExactStackMatcherCache<>(List.of("facade"), () -> {
			int attempt = attempts.incrementAndGet();
			if (attempt == 1) return attempt(false, encoded -> Optional.empty());
			return attempt(true, encoded -> Optional.of(decoded(FACADE, "live")));
		});

		assertFalse(cache.matches(FACADE, "live"::equals));
		assertTrue(cache.matches(FACADE, "live"::equals));
		assertTrue(cache.matches(FACADE, "live"::equals));
		assertEquals(2, attempts.get());
	}

	@Test void malformedSelectorOnLiveRegistryPublishesEmptyBucketOnce() {
		AtomicInteger attempts = new AtomicInteger();
		AtomicInteger decodes = new AtomicInteger();
		var cache = new ExactStackMatcherCache<String>(List.of("malformed"), () -> {
			attempts.incrementAndGet();
			return attempt(true, encoded -> {
				decodes.incrementAndGet();
				return Optional.empty();
			});
		});

		for (int i = 0; i < 10_000; i++) assertFalse(cache.matches(FACADE, ignored -> true));
		assertEquals(1, attempts.get());
		assertEquals(1, decodes.get());
	}

	private static ExactStackMatcherCache<Map<String, Object>> liveCache(
		ExactStackMatcherCache.Decoded<Map<String, Object>> decoded) {
		return new ExactStackMatcherCache<>(List.of("encoded"),
			() -> attempt(true, encoded -> Optional.of(decoded)));
	}

	private static Map<String, Object> componentValue(String color, boolean enabled) {
		return Map.of("appearance", Map.of("color", color, "enabled", enabled));
	}

	private static <T> ExactStackMatcherCache.Decoded<T> decoded(ResourceLocation id, T value) {
		return new ExactStackMatcherCache.Decoded<>(id, value);
	}

	private static <T> ExactStackMatcherCache.DecodeAttempt<T> attempt(boolean live,
		java.util.function.Function<String, Optional<ExactStackMatcherCache.Decoded<T>>> decoder) {
		return new ExactStackMatcherCache.DecodeAttempt<>() {
			@Override public boolean liveRegistry() { return live; }
			@Override public Optional<ExactStackMatcherCache.Decoded<T>> decode(String encodedStack) {
				return decoder.apply(encodedStack);
			}
		};
	}
}
