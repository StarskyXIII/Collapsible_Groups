package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.ingredient.TagQueryResult;
import dev.emi.emi.api.stack.EmiRegistryAdapter;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.util.InheritanceMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class EmiRegistryTagBridge {
	record Tags(boolean available, Set<ResourceLocation> members, Set<ResourceLocation> existing, String reason) {
		static final Tags UNPREPARED = unavailable("unprepared_key");
		static final Tags STANDARD = unavailable("standard_registry");
		Tags {
			members = Set.copyOf(members);
			existing = Set.copyOf(existing);
		}
		static Tags unavailable(String reason) { return new Tags(false, Set.of(), Set.of(), reason); }
		TagQueryResult query(ResourceLocation tag) {
			return !available ? TagQueryResult.UNAVAILABLE
				: members.contains(tag) ? TagQueryResult.MATCH : TagQueryResult.NO_MATCH;
		}
	}

	private final InheritanceMap<EmiRegistryAdapter<?>> adapters;
	private final Map<EmiRegistryAdapter<?>, Registry<?>> registries = new IdentityHashMap<>();
	private final Map<EmiRegistryAdapter<?>, Tags> failures = new IdentityHashMap<>();
	private final Map<Registry<?>, Map<Object, Tags>> resolved = new IdentityHashMap<>();
	private final Map<Registry<?>, Set<ResourceLocation>> existing = new IdentityHashMap<>();
	private final Map<Object, Tags> keys = new IdentityHashMap<>();
	private final String failure;

	private EmiRegistryTagBridge(Map<Class<?>, EmiRegistryAdapter<?>> adapters, String failure) {
		this.adapters = new InheritanceMap<>(adapters);
		this.failure = failure;
	}

	static EmiRegistryTagBridge capture() {
		try {
			return new EmiRegistryTagBridge(Map.copyOf(EmiTags.ADAPTERS_BY_CLASS.map()), "");
		} catch (RuntimeException | LinkageError error) {
			return new EmiRegistryTagBridge(Map.of(), error.getClass().getSimpleName());
		}
	}

	EmiRegistryTagBridge(Map<Class<?>, EmiRegistryAdapter<?>> adapters) {
		this(Map.copyOf(adapters), "");
	}

	Tags resolve(Object key) {
		if (key == null) return Tags.unavailable("missing_key");
		return keys.computeIfAbsent(key, this::resolveKey);
	}

	private Tags resolveKey(Object key) {
		if (!failure.isEmpty()) return Tags.unavailable(failure);
		EmiRegistryAdapter<?> adapter = adapters.get(key.getClass());
		if (adapter == null) return Tags.unavailable("missing_adapter");
		if (failures.containsKey(adapter)) return failures.get(adapter);
		try {
			if (!registries.containsKey(adapter)) registries.put(adapter, adapter.getRegistry());
			Registry<?> registry = registries.get(adapter);
			if (registry == null) return Tags.unavailable("missing_registry");
			return resolved.computeIfAbsent(registry, ignored -> new IdentityHashMap<>())
				.computeIfAbsent(key, value -> read(registry, value));
		} catch (RuntimeException | LinkageError error) {
			Tags result = Tags.unavailable(error.getClass().getSimpleName());
			failures.put(adapter, result);
			return result;
		}
	}

	private <T> Tags read(Registry<T> registry, Object key) {
		@SuppressWarnings("unchecked") T value = (T) key;
		var resourceKey = registry.getResourceKey(value);
		if (resourceKey.isEmpty()) return Tags.unavailable("unregistered_key");
		var holder = registry.getHolder(resourceKey.get());
		if (holder.isEmpty() || !holder.get().isBound() || holder.get().value() != key)
			return Tags.unavailable("invalid_holder");
		Set<ResourceLocation> names = existing.computeIfAbsent(registry, ignored ->
			registry.getTagNames().map(tag -> tag.location()).collect(Collectors.toUnmodifiableSet()));
		return new Tags(true, holder.get().tags().map(tag -> tag.location()).collect(Collectors.toSet()), names, "");
	}

	Map<Object, Tags> snapshot() {
		return Collections.unmodifiableMap(new IdentityHashMap<>(keys));
	}
}
