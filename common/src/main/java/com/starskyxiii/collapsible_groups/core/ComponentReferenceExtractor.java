package com.starskyxiii.collapsible_groups.core;

import com.google.gson.JsonElement;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Extracts encodable, effective data components from a reference ItemStack. */
public final class ComponentReferenceExtractor {
	private ComponentReferenceExtractor() {}

	public record ComponentReference(
		String componentTypeId,
		JsonElement encodedJson,
		String encodedValue,
		boolean fromPatch
	) {}

	public record EffectiveEntry<K, V>(K key, V value) {}

	public record PatchEntry<K>(K key, boolean removed) {}

	@FunctionalInterface
	public interface ValueEncoder<O, K, V> {
		Optional<JsonElement> encode(O ops, K key, V value);
	}

	/**
	 * Production adapter. The effective source is {@link ItemStack#getComponents()}; the
	 * patch is consulted only for provenance and explicit-removal suppression.
	 */
	public static List<ComponentReference> extract(ItemStack stack) {
		List<EffectiveEntry<DataComponentType<?>, TypedDataComponent<?>>> effective = new ArrayList<>();
		for (TypedDataComponent<?> component : stack.getComponents()) {
			effective.add(new EffectiveEntry<>(component.type(), component));
		}
		List<PatchEntry<DataComponentType<?>>> patch = new ArrayList<>();
		for (var entry : stack.getComponentsPatch().entrySet()) {
			patch.add(new PatchEntry<>(entry.getKey(), entry.getValue().isEmpty()));
		}
		RegistryOps<JsonElement> ops = GroupItemSelector.serializationContext();
		return extractEffective(
			effective,
			patch,
			ops,
			type -> {
				Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
				return id == null ? null : id.toString();
			},
			(encodeOps, type, component) -> type.codec() == null
				? Optional.empty()
				: component.encodeValue(encodeOps).result()
		);
	}

	/**
	 * Pure merge/sort/normalize core. Ops and encoding are injected so tests never need
	 * the client-backed registry context used by the production adapter.
	 */
	public static <O, K, V> List<ComponentReference> extractEffective(
		Iterable<EffectiveEntry<K, V>> effectiveEntries,
		Iterable<PatchEntry<K>> patchEntries,
		O ops,
		Function<K, @Nullable String> idLookup,
		ValueEncoder<O, K, V> encoder
	) {
		Map<K, Boolean> patchState = new HashMap<>();
		for (PatchEntry<K> patch : patchEntries) {
			patchState.put(patch.key(), !patch.removed());
		}

		Map<String, ComponentReference> byId = new HashMap<>();
		for (EffectiveEntry<K, V> effective : effectiveEntries) {
			Boolean patchHasValue = patchState.get(effective.key());
			if (Boolean.FALSE.equals(patchHasValue)) {
				continue;
			}
			String id;
			try {
				id = idLookup.apply(effective.key());
			} catch (RuntimeException ignored) {
				continue;
			}
			if (id == null || id.isBlank()) {
				continue;
			}

			Optional<JsonElement> encoded;
			try {
				encoded = encoder.encode(ops, effective.key(), effective.value());
			} catch (RuntimeException ignored) {
				continue;
			}
			if (encoded == null || encoded.isEmpty() || encoded.get() == null) {
				continue;
			}
			JsonElement json = encoded.get().deepCopy();
			ComponentReference reference = new ComponentReference(
				id, json, EncodedValueNormalizer.normalize(json), Boolean.TRUE.equals(patchHasValue));
			ComponentReference previous = byId.get(id);
			if (previous == null || (!previous.fromPatch() && reference.fromPatch())) {
				byId.put(id, reference);
			}
		}

		return byId.values().stream()
			.sorted(Comparator.comparing(ComponentReference::fromPatch).reversed()
				.thenComparing(ComponentReference::componentTypeId))
			.toList();
	}
}
