package com.starskyxiii.collapsible_groups.ingredient;

import com.google.gson.JsonElement;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataAccess;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataAccesses;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.starskyxiii.collapsible_groups.group.filter.EncodedValueNormalizer;
import com.starskyxiii.collapsible_groups.internal.version.data.Minecraft1201NbtAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class ItemStackIngredientView implements IngredientView {
	private static final String STACK_PREFIX = "stack:";
	private static final ItemDataAccess<ItemStack, JsonElement> DATA_ACCESS = ItemDataAccesses.current();

	private final ItemStack stack;
	private final ResourceLocation itemId;

	public ItemStackIngredientView(ItemStack stack) {
		this.stack = stack;
		this.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
	}

	public ItemStack stack() { return stack; }

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
	public boolean hasBlockTag(ResourceLocation tagId) {
		if (stack.getItem() instanceof BlockItem blockItem) {
			return blockItem.getBlock().builtInRegistryHolder().is(TagKey.create(Registries.BLOCK, tagId));
		}
		return false;
	}

	@Override
	public boolean matchesExactStack(String encodedStack) {
		return GroupItemSelector.decodeExactSelector(STACK_PREFIX + encodedStack)
			.map(decoded -> DATA_ACCESS.exactStacks().equivalent(decoded, stack))
			.orElse(false);
	}

	@Override
	public boolean matchesDecodedExactStack(ItemStack decoded) {
		// the reference was already decoded (and normalizedCopy'd) once by the folded
		// exact-stack node; here we only normalize the candidate and compare item + components.
		return DATA_ACCESS.exactStacks().equivalent(decoded, stack);
	}

	@Override
	public boolean hasComponent(String componentTypeId, String encodedValue) {
		return DATA_ACCESS.matchesDataValue(stack, componentTypeId, encodedValue);
	}

	@Override
	public boolean hasComponentPath(String componentTypeId, String path, String expectedValue) {
		return DATA_ACCESS.matchesDataPath(stack, componentTypeId, path, expectedValue);
	}

	@Override
	public boolean matchesNbt(Minecraft1201NbtAccess.Matcher matcher) {
		return matcher.matches(stack);
	}

	public static boolean matchesEncodedValue(JsonElement encoded, String encodedValue) {
		if (encoded instanceof JsonPrimitive && ((JsonPrimitive) encoded).isString()) {
			return EncodedValueNormalizer.normalize(encoded).equals(encodedValue);
		}
		try {
			return encoded.equals(JsonParser.parseString(encodedValue));
		} catch (RuntimeException e) {
			return EncodedValueNormalizer.normalize(encoded).equals(encodedValue);
		}
	}
}
