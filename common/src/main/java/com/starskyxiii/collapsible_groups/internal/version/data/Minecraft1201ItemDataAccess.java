package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.starskyxiii.collapsible_groups.Constants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class Minecraft1201ItemDataAccess implements ItemDataAccess<ItemStack, JsonElement> {
	private static final Object REGISTRY_IDENTITY = BuiltInRegistries.ITEM;
	private final ExactCodec exactCodec = new ExactCodec();

	@Override
	public ExactStackCodec<ItemStack> exactStacks() {
		return exactCodec;
	}

	@Override
	public boolean matchesDataValue(ItemStack stack, String dataTypeId, String encodedValue) {
		return false;
	}

	@Override
	public boolean matchesDataPath(ItemStack stack, String dataTypeId, String path, String expectedValue) {
		return false;
	}

	@Override
	public List<DataReference<JsonElement>> enumerateData(ItemStack stack) {
		return List.of();
	}

	@Override
	public List<DataPath<JsonElement>> enumeratePaths(JsonElement root) {
		return List.of();
	}

	private final class ExactCodec implements ExactStackCodec<ItemStack> {
		@Override
		public ItemDataFormat format() {
			return MinecraftItemDataFormats.EXACT_STACK_1_20_1;
		}

		@Override
		public ItemStack normalizedCopy(ItemStack stack) {
			ItemStack copy = stack.copy();
			copy.setCount(1);
			return copy;
		}

		@Override
		public Optional<String> encodeLegacy(ItemStack stack) {
			return encodePayload(stack).map(ItemDataPayload::encodedValue);
		}

		@Override
		public Optional<ItemDataPayload> encodePayload(ItemStack stack) {
			try {
				CompoundTag tag = normalizedCopy(stack).save(new CompoundTag());
				return Optional.of(ItemDataPayload.nbt(tag.toString()));
			} catch (RuntimeException e) {
				Constants.LOG.warn("Failed to encode exact group selector for {}", stack, e);
				return Optional.empty();
			}
		}

		@Override
		public Object registryIdentity() {
			return REGISTRY_IDENTITY;
		}

		@Override
		public DecodeSnapshot<ItemStack> beginDecode() {
			return new DecodeSnapshot<ItemStack>() {
				@Override public boolean liveRegistry() { return true; }
				@Override public Object registryIdentity() { return REGISTRY_IDENTITY; }

				@Override
				public Optional<ItemStack> decode(String encoded) {
					try {
						CompoundTag root = TagParser.parseTag(encoded);
						if (!root.contains("id", Tag.TAG_STRING)
							|| !root.contains("Count", Tag.TAG_ANY_NUMERIC)
							|| (root.contains("tag") && !root.contains("tag", Tag.TAG_COMPOUND))) {
							return Optional.empty();
						}
						ItemStack decoded = ItemStack.of(root);
						return decoded.isEmpty() ? Optional.empty() : Optional.of(normalizedCopy(decoded));
					} catch (Exception e) {
						Constants.LOG.warn("Invalid exact group selector data '{}'", encoded, e);
						return Optional.empty();
					}
				}
			};
		}

		@Override
		public boolean equivalent(ItemStack left, ItemStack right) {
			return ItemStack.isSameItemSameTags(left, right);
		}

		@Override
		public String itemId(ItemStack stack) {
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		}
	}
}
