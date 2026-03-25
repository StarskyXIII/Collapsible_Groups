package com.starskyxiii.collapsible_groups.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

public final class ItemStackIngredientView implements IngredientView {
	private static final String STACK_PREFIX = "stack:";

	private final ItemStack stack;
	private final ResourceLocation itemId;

	public ItemStackIngredientView(ItemStack stack) {
		this.stack = stack;
		this.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
	}

	@Override
	public String ingredientType() {
		return "item";
	}

	@Override
	public ResourceLocation resourceLocation() {
		return itemId;
	}

	@Override
	public boolean hasTag(ResourceLocation tagId) {
		return stack.is(TagKey.create(Registries.ITEM, tagId));
	}

	@Override
	public boolean matchesExactStack(String encodedStack) {
		return GroupItemSelector.decodeExactSelector(STACK_PREFIX + encodedStack)
			.map(decoded -> ItemStack.isSameItemSameComponents(decoded, GroupItemSelector.normalizedCopy(stack)))
			.orElse(false);
	}

	@Override
	public boolean hasComponent(String componentTypeId, String encodedValue) {
		ResourceLocation typeId = ResourceLocation.tryParse(componentTypeId);
		if (typeId == null) return false;
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(typeId);
		if (type == null || type.codec() == null) return false;
		return matchesComponentValue(type, encodedValue);
	}

	@Override
	public boolean hasComponentPath(String componentTypeId, String path, String expectedValue) {
		ResourceLocation typeId = ResourceLocation.tryParse(componentTypeId);
		if (typeId == null) return false;
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(typeId);
		if (type == null || type.codec() == null) return false;
		return matchesComponentPath(type, path, expectedValue);
	}

	private <T> boolean matchesComponentPath(DataComponentType<T> type, String path, String expectedValue) {
		T actual = stack.get(type);
		if (actual == null) return false;
		return type.codec()
			.encodeStart(GroupItemSelector.serializationContext(), actual)
			.result()
			.map(encoded -> {
				JsonElement node = ComponentPathNavigator.navigatePath(encoded, path);
				if (node == null) return false;
				return matchesEncodedValue(node, expectedValue);
			})
			.orElse(false);
	}

	private <T> boolean matchesComponentValue(DataComponentType<T> type, String encodedValue) {
		T actual = stack.get(type);
		if (actual == null) return false;
		return type.codec()
			.encodeStart(GroupItemSelector.serializationContext(), actual)
			.result()
			.map(encoded -> matchesEncodedValue(encoded, encodedValue))
			.orElse(false);
	}

	private static boolean matchesEncodedValue(JsonElement encoded, String encodedValue) {
		// For string-encoded components (ResourceLocations, enums), compare the unwrapped value directly.
		if (encoded instanceof JsonPrimitive p && p.isString()) {
			return p.getAsString().equals(encodedValue);
		}
		// For non-string types (numbers, objects, arrays), try structural JSON comparison.
		try {
			JsonElement expected = JsonParser.parseString(encodedValue);
			return encoded.equals(expected);
		} catch (RuntimeException e) {
			// If the encodedValue isn't valid JSON, fall back to toString comparison.
			return encoded.toString().equals(encodedValue);
		}
	}
}
