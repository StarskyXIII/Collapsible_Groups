package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.group.filter.ComponentPathNavigator;
import com.starskyxiii.collapsible_groups.group.filter.EncodedValueNormalizer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class Minecraft121ItemDataAccess implements ItemDataAccess<ItemStack, JsonElement> {
	private static final RegistryAccess.Frozen FALLBACK_REGISTRY_ACCESS =
		RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	private static final AtomicBoolean FALLBACK_WARNING_LOGGED = new AtomicBoolean(false);

	private final ExactCodec exactCodec = new ExactCodec();

	@Override
	public ExactStackCodec<ItemStack> exactStacks() {
		return exactCodec;
	}

	@Override
	public boolean matchesDataValue(ItemStack stack, String dataTypeId, String encodedValue) {
		DataComponentType<?> type = componentType(dataTypeId);
		if (type == null || type.codec() == null) return false;
		return matchesComponentValue(stack, type, encodedValue);
	}

	@Override
	public boolean matchesDataPath(ItemStack stack, String dataTypeId, String path, String expectedValue) {
		DataComponentType<?> type = componentType(dataTypeId);
		if (type == null || type.codec() == null) return false;
		return matchesComponentPath(stack, type, path, expectedValue);
	}

	@Override
	public List<DataReference<JsonElement>> enumerateData(ItemStack stack) {
		List<EffectiveEntry<DataComponentType<?>, TypedDataComponent<?>>> effective = new ArrayList<>();
		for (TypedDataComponent<?> component : stack.getComponents()) {
			effective.add(new EffectiveEntry<>(component.type(), component));
		}
		List<PatchEntry<DataComponentType<?>>> patch = new ArrayList<>();
		for (var entry : stack.getComponentsPatch().entrySet()) {
			patch.add(new PatchEntry<>(entry.getKey(), entry.getValue().isEmpty()));
		}
		RegistryOps<JsonElement> ops = serializationContext();
		return extractEffective(
			effective,
			patch,
			ops,
			type -> {
				ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
				return id == null ? null : id.toString();
			},
			(encodeOps, type, component) -> type.codec() == null
				? Optional.empty()
				: component.encodeValue(encodeOps).result()
		);
	}

	@Override
	public List<DataPath<JsonElement>> enumeratePaths(JsonElement root) {
		return ComponentPathNavigator.enumerateReachable(root).stream()
			.map(path -> new DataPath<>(path.path(), path.value()))
			.toList();
	}

	public RegistryOps<JsonElement> serializationContext() {
		RegistryAccess live = liveRegistryAccess();
		if (live != null) return live.createSerializationContext(JsonOps.INSTANCE);
		warnFallbackOnce();
		return FALLBACK_REGISTRY_ACCESS.createSerializationContext(JsonOps.INSTANCE);
	}

	public RegistryContext registryContext() {
		RegistryAccess live = liveRegistryAccess();
		if (live != null) {
			return new RegistryContext(live.createSerializationContext(JsonOps.INSTANCE), true, live);
		}
		warnFallbackOnce();
		return new RegistryContext(
			FALLBACK_REGISTRY_ACCESS.createSerializationContext(JsonOps.INSTANCE), false, FALLBACK_REGISTRY_ACCESS);
	}

	public record RegistryContext(
		RegistryOps<JsonElement> ops,
		boolean liveRegistry,
		Object registryIdentity
	) {}

	public ExactStackCodec.DecodeSnapshot<ItemStack> decodeSnapshot(
		RegistryOps<JsonElement> ops,
		boolean liveRegistry,
		Object registryIdentity
	) {
		return new MinecraftDecodeSnapshot(ops, liveRegistry, registryIdentity);
	}

	public static VersionedDataEnvelope.Support componentValueSupport(String encoded) {
		return VersionedDataEnvelope.inspect(encoded, MinecraftItemDataFormats.COMPONENT_VALUE_1_21_1).support();
	}

	public static String envelopeComponentValue(JsonElement encoded) {
		return VersionedDataEnvelope.wrap(MinecraftItemDataFormats.COMPONENT_VALUE_1_21_1, encoded);
	}

	public static boolean matchesEncodedValue(JsonElement encoded, String expectedValue) {
		VersionedDataEnvelope.Inspection inspection =
			VersionedDataEnvelope.inspect(expectedValue, MinecraftItemDataFormats.COMPONENT_VALUE_1_21_1);
		if (VersionedDataEnvelope.isEnvelope(expectedValue)
			&& inspection.support() != VersionedDataEnvelope.Support.CURRENT) return false;
		if (inspection.support() == VersionedDataEnvelope.Support.CURRENT) {
			return serializedValueEquals(encoded, inspection.data().orElseThrow());
		}
		if (encoded instanceof JsonPrimitive primitive && primitive.isString()) {
			return EncodedValueNormalizer.normalize(encoded).equals(expectedValue);
		}
		try {
			return serializedValueEquals(encoded, JsonParser.parseString(expectedValue));
		} catch (RuntimeException e) {
			return EncodedValueNormalizer.normalize(encoded).equals(expectedValue);
		}
	}

	private static boolean serializedValueEquals(JsonElement encoded, JsonElement expected) {
		if (encoded.equals(expected)) return true;
		try {
			JsonElement reparsed = JsonParser.parseString(encoded.toString());
			return sameJsonTypes(encoded, reparsed) && reparsed.equals(expected);
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static boolean sameJsonTypes(JsonElement encoded, JsonElement reparsed) {
		if (encoded.isJsonNull() || reparsed.isJsonNull()) {
			return encoded.isJsonNull() && reparsed.isJsonNull();
		}
		if (encoded.isJsonPrimitive() || reparsed.isJsonPrimitive()) {
			if (!encoded.isJsonPrimitive() || !reparsed.isJsonPrimitive()) return false;
			JsonPrimitive left = encoded.getAsJsonPrimitive();
			JsonPrimitive right = reparsed.getAsJsonPrimitive();
			return left.isNumber() == right.isNumber()
				&& left.isString() == right.isString()
				&& left.isBoolean() == right.isBoolean();
		}
		if (encoded.isJsonArray() || reparsed.isJsonArray()) {
			if (!encoded.isJsonArray() || !reparsed.isJsonArray()) return false;
			var left = encoded.getAsJsonArray();
			var right = reparsed.getAsJsonArray();
			if (left.size() != right.size()) return false;
			for (int index = 0; index < left.size(); index++) {
				if (!sameJsonTypes(left.get(index), right.get(index))) return false;
			}
			return true;
		}
		if (!encoded.isJsonObject() || !reparsed.isJsonObject()) return false;
		var left = encoded.getAsJsonObject();
		var right = reparsed.getAsJsonObject();
		if (left.size() != right.size()) return false;
		for (var entry : left.entrySet()) {
			JsonElement rightValue = right.get(entry.getKey());
			if (rightValue == null || !sameJsonTypes(entry.getValue(), rightValue)) return false;
		}
		return true;
	}

	public record EffectiveEntry<K, V>(K key, V value) {}

	public record PatchEntry<K>(K key, boolean removed) {}

	@FunctionalInterface
	public interface ValueEncoder<O, K, V> {
		Optional<JsonElement> encode(O ops, K key, V value);
	}

	public static <O, K, V> List<DataReference<JsonElement>> extractEffective(
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

		Map<String, DataReference<JsonElement>> byId = new HashMap<>();
		for (EffectiveEntry<K, V> effective : effectiveEntries) {
			Boolean patchHasValue = patchState.get(effective.key());
			if (Boolean.FALSE.equals(patchHasValue)) continue;
			String id;
			try {
				id = idLookup.apply(effective.key());
			} catch (RuntimeException ignored) {
				continue;
			}
			if (id == null || id.isBlank()) continue;

			Optional<JsonElement> encoded;
			try {
				encoded = encoder.encode(ops, effective.key(), effective.value());
			} catch (RuntimeException ignored) {
				continue;
			}
			if (encoded == null || encoded.isEmpty() || encoded.get() == null) continue;
			JsonElement json = encoded.get().deepCopy();
			DataReference<JsonElement> reference = new DataReference<>(
				id, json, EncodedValueNormalizer.normalize(json), Boolean.TRUE.equals(patchHasValue));
			DataReference<JsonElement> previous = byId.get(id);
			if (previous == null || (!previous.fromPatch() && reference.fromPatch())) {
				byId.put(id, reference);
			}
		}

		return byId.values().stream()
			.sorted(Comparator.comparing((DataReference<JsonElement> reference) -> reference.fromPatch()).reversed()
				.thenComparing(DataReference::dataTypeId))
			.toList();
	}

	private static DataComponentType<?> componentType(String componentTypeId) {
		ResourceLocation typeId = ResourceLocation.tryParse(componentTypeId);
		return typeId == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(typeId);
	}

	private <T> boolean matchesComponentPath(
		ItemStack stack,
		DataComponentType<T> type,
		String path,
		String expectedValue
	) {
		T actual = stack.get(type);
		if (actual == null) return false;
		return type.codec().encodeStart(serializationContext(), actual).result()
			.map(encoded -> {
				JsonElement node = ComponentPathNavigator.navigatePath(encoded, path);
				return node != null && matchesEncodedValue(node, expectedValue);
			})
			.orElse(false);
	}

	private <T> boolean matchesComponentValue(ItemStack stack, DataComponentType<T> type, String expectedValue) {
		T actual = stack.get(type);
		if (actual == null) return false;
		return type.codec().encodeStart(serializationContext(), actual).result()
			.map(encoded -> matchesEncodedValue(encoded, expectedValue))
			.orElse(false);
	}

	private static RegistryAccess liveRegistryAccess() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) return null;
		if (minecraft.level != null) return minecraft.level.registryAccess();
		if (minecraft.getConnection() != null) return minecraft.getConnection().registryAccess();
		if (minecraft.player != null) return minecraft.player.registryAccess();
		return null;
	}

	private static Object currentRegistryIdentity() {
		RegistryAccess live = liveRegistryAccess();
		return live == null ? FALLBACK_REGISTRY_ACCESS : live;
	}

	private static void warnFallbackOnce() {
		if (FALLBACK_WARNING_LOGGED.compareAndSet(false, true)) {
			Constants.LOG.warn(
				"Exact group selector serialization is using built-in fallback registries before a live client registry is available."
			);
		}
	}

	private final class ExactCodec implements ExactStackCodec<ItemStack> {
		@Override public ItemDataFormat format() { return MinecraftItemDataFormats.EXACT_STACK_1_21_1; }

		@Override
		public ItemStack normalizedCopy(ItemStack stack) {
			ItemStack copy = stack.copy();
			copy.setCount(1);
			return copy;
		}

		@Override
		public Optional<String> encodeLegacy(ItemStack stack) {
			ItemStack normalized = normalizedCopy(stack);
			return ItemStack.STRICT_SINGLE_ITEM_CODEC.encodeStart(serializationContext(), normalized)
				.resultOrPartial(error -> Constants.LOG.warn(
					"Failed to encode exact group selector for {}: {}", normalized, error))
				.map(JsonElement::toString);
		}

		@Override
		public Optional<String> encodeEnvelope(ItemStack stack) {
			ItemStack normalized = normalizedCopy(stack);
			return ItemStack.STRICT_SINGLE_ITEM_CODEC.encodeStart(serializationContext(), normalized)
				.resultOrPartial(error -> Constants.LOG.warn(
					"Failed to encode exact group selector for {}: {}", normalized, error))
				.map(encoded -> VersionedDataEnvelope.wrap(format(), encoded));
		}

		@Override public Object registryIdentity() { return currentRegistryIdentity(); }

		@Override
		public DecodeSnapshot<ItemStack> beginDecode() {
			RegistryContext context = registryContext();
			return new MinecraftDecodeSnapshot(context.ops(), context.liveRegistry(), context.registryIdentity());
		}

		@Override
		public boolean equivalent(ItemStack left, ItemStack right) {
			return ItemStack.isSameItemSameComponents(left, right);
		}

		@Override
		public String itemId(ItemStack stack) {
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		}
	}

	private final class MinecraftDecodeSnapshot implements ExactStackCodec.DecodeSnapshot<ItemStack> {
		private final RegistryOps<JsonElement> ops;
		private final boolean liveRegistry;
		private final Object registryIdentity;

		private MinecraftDecodeSnapshot(RegistryOps<JsonElement> ops, boolean liveRegistry, Object registryIdentity) {
			this.ops = ops;
			this.liveRegistry = liveRegistry;
			this.registryIdentity = registryIdentity;
		}

		@Override public boolean liveRegistry() { return liveRegistry; }
		@Override public Object registryIdentity() { return registryIdentity; }

		@Override
		public Optional<ItemStack> decode(String encoded) {
			try {
				VersionedDataEnvelope.Inspection inspection =
					VersionedDataEnvelope.inspect(encoded, MinecraftItemDataFormats.EXACT_STACK_1_21_1);
				if (inspection.support() == VersionedDataEnvelope.Support.UNSUPPORTED
					|| inspection.support() == VersionedDataEnvelope.Support.MALFORMED) return Optional.empty();
				return ItemStack.STRICT_SINGLE_ITEM_CODEC.parse(ops, inspection.data().orElseThrow())
					.resultOrPartial(error -> Constants.LOG.warn(
						"Failed to decode exact group selector data '{}': {}", encoded, error))
					.map(exactCodec::normalizedCopy);
			} catch (RuntimeException e) {
				Constants.LOG.warn("Invalid exact group selector data '{}'", encoded, e);
				return Optional.empty();
			}
		}
	}
}
