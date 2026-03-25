package com.starskyxiii.collapsible_groups.core;

import com.starskyxiii.collapsible_groups.Constants;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GroupItemSelector {
	private static final String STACK_PREFIX = "stack:";
	private static final RegistryAccess.Frozen FALLBACK_REGISTRY_ACCESS =
		RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	private static final AtomicBoolean FALLBACK_WARNING_LOGGED = new AtomicBoolean(false);

	private GroupItemSelector() {}

	public static boolean isWholeItemSelector(String selector) {
		return !isExactSelector(selector);
	}

	public static boolean isExactSelector(String selector) {
		return selector.startsWith(STACK_PREFIX);
	}

	public static boolean isSelectorForSameItem(String selector, ItemStack stack) {
		if (isWholeItemSelector(selector)) {
			return selector.equals(wholeItemSelector(stack));
		}
		return decodeExactSelector(selector)
			.map(decoded -> sameItem(decoded, stack))
			.orElse(false);
	}

	public static boolean sameItem(ItemStack left, ItemStack right) {
		return left.getItem() == right.getItem();
	}

	public static ItemStack normalizedCopy(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.setCount(1);
		return copy;
	}

	public static String wholeItemSelector(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static String exactSelector(ItemStack stack) {
		return tryExactSelector(stack)
			.orElseThrow(() -> new IllegalStateException("Failed to encode exact group selector"));
	}

	public static Optional<String> tryExactSelector(ItemStack stack) {
		ItemStack normalized = normalizedCopy(stack);
		return ItemStack.STRICT_SINGLE_ITEM_CODEC
			.encodeStart(serializationContext(), normalized)
			.resultOrPartial(error -> Constants.LOG.warn("Failed to encode exact group selector for {}: {}", normalized, error))
			.map(encoded -> STACK_PREFIX + encoded);
	}

	public static Optional<ItemStack> decodeExactSelector(String selector) {
		if (!isExactSelector(selector)) {
			return Optional.empty();
		}

		try {
			JsonElement encoded = JsonParser.parseString(selector.substring(STACK_PREFIX.length()));
			return ItemStack.STRICT_SINGLE_ITEM_CODEC.parse(serializationContext(), encoded)
				.resultOrPartial(error -> Constants.LOG.warn("Failed to decode exact group selector '{}': {}", selector, error))
				.map(GroupItemSelector::normalizedCopy);
		} catch (RuntimeException e) {
			Constants.LOG.warn("Invalid exact group selector '{}'", selector, e);
			return Optional.empty();
		}
	}

	static RegistryOps<JsonElement> serializationContext() {
		return registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	private static RegistryAccess registryAccess() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			return minecraft.level.registryAccess();
		}
		if (minecraft.getConnection() != null) {
			return minecraft.getConnection().registryAccess();
		}
		if (minecraft.player != null) {
			return minecraft.player.registryAccess();
		}
		if (FALLBACK_WARNING_LOGGED.compareAndSet(false, true)) {
			Constants.LOG.warn(
				"Exact group selector serialization is using built-in fallback registries before a live client registry is available."
			);
		}
		return FALLBACK_REGISTRY_ACCESS;
	}
}
