package com.starskyxiii.collapsible_groups.group.filter;

import com.google.gson.JsonElement;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataAccess;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataAccesses;
import com.starskyxiii.collapsible_groups.internal.version.data.Minecraft121ItemDataAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
		List<Minecraft121ItemDataAccess.EffectiveEntry<K, V>> effective = new java.util.ArrayList<>();
		for (EffectiveEntry<K, V> entry : effectiveEntries) {
			effective.add(new Minecraft121ItemDataAccess.EffectiveEntry<>(entry.key(), entry.value()));
		}
		List<Minecraft121ItemDataAccess.PatchEntry<K>> patch = new java.util.ArrayList<>();
		for (PatchEntry<K> entry : patchEntries) {
			patch.add(new Minecraft121ItemDataAccess.PatchEntry<>(entry.key(), entry.removed()));
		}
		return Minecraft121ItemDataAccess.extractEffective(
			effective, patch, ops, idLookup, encoder::encode)
			.stream()
			.map(ComponentReferenceExtractor::toComponentReference)
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
