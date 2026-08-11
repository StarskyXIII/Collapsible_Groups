package com.starskyxiii.collapsible_groups.group.filter;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** Lazy immutable item-id bucket shared by singleton and folded exact-stack selectors. */
final class ExactStackMatcherCache<T> {
	interface Decoder<T> {
		DecodeAttempt<T> beginAttempt();
	}

	interface DecodeAttempt<T> {
		boolean liveRegistry();
		Optional<Decoded<T>> decode(String encodedStack);
	}

	record Decoded<T>(ResourceLocation itemId, T value) {}

	private final List<String> encodedStacks;
	private final Decoder<T> decoder;
	private volatile Map<ResourceLocation, List<T>> bucket;

	ExactStackMatcherCache(List<String> encodedStacks, Decoder<T> decoder) {
		this.encodedStacks = List.copyOf(encodedStacks);
		this.decoder = decoder;
	}

	boolean matches(ResourceLocation itemId, Predicate<T> exactMatcher) {
		if (itemId == null) return false;
		Map<ResourceLocation, List<T>> resolved = resolveBucket();
		if (resolved == null) return false;
		List<T> references = resolved.get(itemId);
		if (references == null) return false;
		for (T reference : references) {
			if (exactMatcher.test(reference)) return true;
		}
		return false;
	}

	private Map<ResourceLocation, List<T>> resolveBucket() {
		Map<ResourceLocation, List<T>> local = bucket;
		if (local != null) return local;
		synchronized (this) {
			local = bucket;
			if (local != null) return local;
			Map<ResourceLocation, List<T>> built = buildBucket();
			if (built != null) bucket = built;
			return built;
		}
	}

	private Map<ResourceLocation, List<T>> buildBucket() {
		DecodeAttempt<T> attempt = decoder.beginAttempt();
		Map<ResourceLocation, List<T>> mutable = new LinkedHashMap<>();
		int decoded = 0;
		for (String encodedStack : encodedStacks) {
			Optional<Decoded<T>> reference = attempt.decode(encodedStack);
			if (reference.isEmpty()) continue;
			Decoded<T> decodedReference = reference.get();
			mutable.computeIfAbsent(decodedReference.itemId(), ignored -> new ArrayList<>())
				.add(decodedReference.value());
			decoded++;
		}
		if (decoded == 0 && !attempt.liveRegistry()) return null;
		Map<ResourceLocation, List<T>> immutable = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, List<T>> entry : mutable.entrySet()) {
			immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return Map.copyOf(immutable);
	}
}
