package com.starskyxiii.collapsible_groups.group.filter;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Lazy immutable item-id bucket shared by singleton and folded exact-stack selectors. */
final class ExactStackMatcherCache<T> {
	interface Decoder<T> {
		DecodeAttempt<T> beginAttempt();
	}

	interface DecodeAttempt<T> {
		boolean liveRegistry();
		default Object registryIdentity() { return this; }
		Optional<Decoded<T>> decode(String encodedStack);
	}

	record Decoded<T>(ResourceLocation itemId, T value) {}

	private final List<String> encodedStacks;
	private final Decoder<T> decoder;
	private final Supplier<Object> registryIdentity;
	private final boolean identityAware;
	private volatile ResolvedBucket<T> bucket;

	ExactStackMatcherCache(List<String> encodedStacks, Decoder<T> decoder) {
		Object stableIdentity = new Object();
		this.encodedStacks = List.copyOf(encodedStacks);
		this.decoder = decoder;
		this.registryIdentity = () -> stableIdentity;
		this.identityAware = false;
	}

	ExactStackMatcherCache(List<String> encodedStacks, Decoder<T> decoder, Supplier<Object> registryIdentity) {
		this.encodedStacks = List.copyOf(encodedStacks);
		this.decoder = decoder;
		this.registryIdentity = registryIdentity;
		this.identityAware = true;
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
		Object currentIdentity = registryIdentity.get();
		ResolvedBucket<T> local = bucket;
		if (local != null && local.registryIdentity() == currentIdentity) return local.references();
		synchronized (this) {
			currentIdentity = registryIdentity.get();
			local = bucket;
			if (local != null && local.registryIdentity() == currentIdentity) return local.references();
			ResolvedBucket<T> built = buildBucket(currentIdentity);
			if (built == null || (identityAware && registryIdentity.get() != built.registryIdentity())) return null;
			bucket = built;
			return built.references();
		}
	}

	private ResolvedBucket<T> buildBucket(Object requestedIdentity) {
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
		Object publishedIdentity = identityAware ? attempt.registryIdentity() : requestedIdentity;
		return new ResolvedBucket<>(publishedIdentity, Map.copyOf(immutable));
	}

	private record ResolvedBucket<T>(Object registryIdentity, Map<ResourceLocation, List<T>> references) {}
}
