package com.starskyxiii.collapsible_groups.ingredient;

import com.google.gson.JsonElement;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import com.starskyxiii.collapsible_groups.internal.version.data.ExactStackCodec;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataAccesses;
import com.starskyxiii.collapsible_groups.internal.version.data.Minecraft121ItemDataAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class GroupItemSelector {
	private static final String STACK_PREFIX = "stack:";
	private static final Minecraft121ItemDataAccess DATA_ACCESS = ItemDataAccesses.minecraft121();
	private static final ExactStackCodec<ItemStack> EXACT_STACKS = DATA_ACCESS.exactStacks();

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
		return EXACT_STACKS.normalizedCopy(stack);
	}

	public static String wholeItemSelector(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static String exactSelector(ItemStack stack) {
		return tryExactSelector(stack)
			.orElseThrow(() -> new IllegalStateException("Failed to encode exact group selector"));
	}

	public static Optional<String> tryExactSelector(ItemStack stack) {
		return EXACT_STACKS.encodeLegacy(stack).map(encoded -> STACK_PREFIX + encoded);
	}

	public static Optional<ItemDataPayload> tryExactPayload(ItemStack stack) {
        return EXACT_STACKS.encodePayload(stack);
    }

	public static Optional<ItemStack> decodeExactSelector(String selector) {
		return decodeExactSelector(selector, exactDecodeContext());
	}

	/**
	 * a single registry-resolution snapshot for a batch of exact-selector decodes.
	 * {@link #liveRegistry()} reports whether this snapshot resolved a live client registry
	 * (world/connection/player) or fell back to the built-in registries. Callers that cache decode
	 * results must base their "was the registry ready?" decision on the snapshot that actually
	 * performed the decodes — not on a separate, later observation of {@code Minecraft} state —
	 * otherwise the game state can change between decode and decision (TOCTOU) and an all-failed
	 * fallback decode could be cached permanently.
	 */
	public record ExactDecodeContext(RegistryOps<JsonElement> ops, boolean liveRegistry, Object registryIdentity) {}

	/** captures the current registry resolution once, for use across a batch of decodes. */
	public static ExactDecodeContext exactDecodeContext() {
		Minecraft121ItemDataAccess.RegistryContext context = DATA_ACCESS.registryContext();
		return new ExactDecodeContext(context.ops(), context.liveRegistry(), context.registryIdentity());
	}

	/**
	 * decodes against a caller-held {@link ExactDecodeContext} snapshot, so every decode in
	 * a batch uses the same registry resolution that the caller's caching decision will inspect.
	 */
	public static Optional<ItemStack> decodeExactSelector(String selector, ExactDecodeContext context) {
		if (!isExactSelector(selector)) {
			return Optional.empty();
		}

		return DATA_ACCESS.decodeSnapshot(context.ops(), context.liveRegistry(), context.registryIdentity())
			.decode(selector.substring(STACK_PREFIX.length()));
	}

	public static RegistryOps<JsonElement> serializationContext() {
		return DATA_ACCESS.serializationContext();
	}

	public static Object registryIdentity() {
		return EXACT_STACKS.registryIdentity();
	}
}
