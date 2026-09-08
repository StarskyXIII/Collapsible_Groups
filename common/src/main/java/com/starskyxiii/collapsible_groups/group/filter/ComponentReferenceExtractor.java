package com.starskyxiii.collapsible_groups.group.filter;

import com.google.gson.JsonElement;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataAccess;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataAccesses;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
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

	public static List<ComponentReference> extract(ItemStack stack) {
		return ItemDataAccesses.current().enumerateData(stack).stream()
			.map(ComponentReferenceExtractor::toComponentReference)
			.toList();
	}

	public static List<ComponentPathNavigator.PathNode> enumeratePaths(ComponentReference reference) {
		return ItemDataAccesses.current().enumeratePaths(reference.encodedJson()).stream()
			.map(path -> new ComponentPathNavigator.PathNode(path.path(), path.value()))
			.toList();
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
		for (PatchEntry<K> entry : patchEntries) {
			patchState.put(entry.key(), !entry.removed());
		}
		Map<String, ComponentReference> byId = new HashMap<>();
		for (EffectiveEntry<K, V> entry : effectiveEntries) {
			Boolean patchHasValue = patchState.get(entry.key());
			if (Boolean.FALSE.equals(patchHasValue)) continue;
			String id;
			Optional<JsonElement> encoded;
			try {
				id = idLookup.apply(entry.key());
				encoded = encoder.encode(ops, entry.key(), entry.value());
			} catch (RuntimeException ignored) {
				continue;
			}
			if (id == null || id.isBlank() || encoded == null || encoded.isEmpty() || encoded.get() == null) continue;
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

	private static ComponentReference toComponentReference(ItemDataAccess.DataReference<JsonElement> reference) {
		return new ComponentReference(
			reference.dataTypeId(),
			reference.encodedValueNode(),
			reference.encodedValue(),
			reference.fromPatch());
	}
}
