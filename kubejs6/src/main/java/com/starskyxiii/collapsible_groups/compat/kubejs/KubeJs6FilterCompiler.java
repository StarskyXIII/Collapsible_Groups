package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.regexp.NativeRegExp;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class KubeJs6FilterCompiler {
	private static final Set<String> MAP_KEYS = Set.of("item", "tag", "nbt");
	private static final String FORGE_STRICT_NBT = "net.minecraftforge.common.crafting.StrictNBTIngredient";

	private KubeJs6FilterCompiler() {}

	public static GroupFilter compileItem(Object input) {
		try {
			return compileItemUnchecked(unwrap(input));
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static GroupFilter compileItemUnchecked(Object input) {
		if (input == null || input instanceof BaseFunction || input instanceof Pattern
			|| input instanceof NativeRegExp) return null;
		if (input instanceof GroupFilter filter) {
			return KubeJsFilterComposition.supportsTree(filter) ? filter : null;
		}
		if (input instanceof ItemStack stack) {
			return stack.isEmpty() ? null : Filters.exactStack(stack);
		}
		if (input instanceof ItemLike item) {
			return Filters.itemId(BuiltInRegistries.ITEM.getKey(item.asItem()).toString());
		}
		if (input instanceof Ingredient ingredient) return compileIngredient(ingredient);
		if (input instanceof CharSequence text) return compileString(text.toString());
		if (input instanceof Map<?, ?> map) return compileMap(map);
		if (input instanceof Iterable<?> iterable) return compileIterable(iterable);
		if (input instanceof Object[] array) return compileIterable(List.of(array));
		return null;
	}

	private static GroupFilter compileString(String input) {
		String value = input.trim();
		if (value.isEmpty() || value.equals("-") || value.equals("*") || value.startsWith("%")
			|| value.startsWith("/") && value.endsWith("/")) return null;
		if (value.startsWith("#")) {
			String id = value.substring(1);
			return ResourceLocation.tryParse(id) == null ? null : Filters.itemTag(id);
		}
		if (value.startsWith("@")) {
			String namespace = value.substring(1);
			ResourceLocation probe = ResourceLocation.tryParse(namespace + ":probe");
			return probe != null && probe.getNamespace().equals(namespace) ? Filters.itemNamespace(namespace) : null;
		}
		return ResourceLocation.tryParse(value) == null ? null : Filters.itemId(value);
	}

	private static GroupFilter compileMap(Map<?, ?> map) {
		if (map.isEmpty() || !MAP_KEYS.containsAll(map.keySet())) return null;
		if (map.containsKey("tag")) {
			if (map.size() != 1 || !(map.get("tag") instanceof CharSequence value)) return null;
			return compileString("#" + value);
		}
		if (!(map.get("item") instanceof CharSequence value) || ResourceLocation.tryParse(value.toString()) == null) return null;
		return !map.containsKey("nbt") && map.size() == 1 ? Filters.itemId(value.toString()) : null;
	}

	private static GroupFilter compileIterable(Iterable<?> values) {
		List<GroupFilter> children = new ArrayList<>();
		for (Object value : values) {
			GroupFilter child = compileItemUnchecked(unwrap(value));
			if (child == null) return null;
			children.add(child);
		}
		return KubeJsFilterComposition.any(children);
	}

	private static GroupFilter compileIngredient(Ingredient ingredient) {
		if (isForgeStrictNbt(ingredient)) return null;
		if (ingredient.isEmpty()) return null;
		String customClass = customIngredientClass(ingredient);
		boolean vanilla = ingredient.getClass() == Ingredient.class && customClass.isEmpty();
		return compileIngredientJson(ingredient.toJson(), ingredient, vanilla, customClass);
	}

	private static GroupFilter compileIngredientJson(JsonElement json, Ingredient ingredient, boolean vanilla,
		String customClass) {
		if (json == null || json.isJsonNull()) return null;
		if (json.isJsonArray()) {
			JsonArray array = json.getAsJsonArray();
			List<GroupFilter> children = new ArrayList<>(array.size());
			for (JsonElement element : array) {
				GroupFilter child = compileIngredientJson(element, ingredient, vanilla, customClass);
				if (child == null) return null;
				children.add(child);
			}
			return KubeJsFilterComposition.any(children);
		}
		if (!json.isJsonObject()) return null;
		JsonObject object = json.getAsJsonObject();
		if (vanilla && object.has("item") && object.size() == 1) return compileString(object.get("item").getAsString());
		if (vanilla && object.has("tag") && object.size() == 1) return compileString("#" + object.get("tag").getAsString());
		boolean strictFabricNbt = isStrictFabricNbt(object, customClass);
		if (!strictFabricNbt) return null;
		ItemStack[] items = ingredient.getItems();
		if (items.length != 1 || items[0].isEmpty()) return null;
		if (strictFabricNbt && !hasPlainItemBase(ingredient, object, items[0])) return null;
		return Filters.exactStack(items[0]);
	}

	static String unsupportedReason(Object input) {
		try {
			Object value = unwrap(input);
			if (value instanceof Ingredient ingredient && isForgeStrictNbt(ingredient)) {
				return "Forge strongNBT() uses share-tag semantics that cannot be represented as a complete exact stack. "
					+ "Pass the ItemStack directly or use source.exact(...).";
			}
		} catch (RuntimeException ignored) {
		}
		return "Ingredient uses counts, an arbitrary predicate, partial NBT, or an unsupported custom condition.";
	}

	private static boolean isForgeStrictNbt(Ingredient ingredient) {
		return ingredient.getClass().getName().equals(FORGE_STRICT_NBT);
	}

	static boolean isStrictFabricNbt(JsonObject object, String customClass) {
		return object.has("fabric:type") && object.get("fabric:type").getAsString().equals("kubejs:nbt")
			&& object.has("strict") && object.get("strict").getAsBoolean()
			&& customClass.equals("dev.latvian.mods.kubejs.platform.fabric.ingredient.KubeJSNbtIngredient");
	}

	private static boolean hasPlainItemBase(Ingredient ingredient, JsonObject object, ItemStack stack) {
		if (!object.has("base")) return false;
		Ingredient base = fabricNbtBase(ingredient);
		if (base == null || base.getClass() != Ingredient.class || !customIngredientClass(base).isEmpty()) return false;
		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		return isSameExplicitItem(object.get("base"), itemId);
	}

	private static Ingredient fabricNbtBase(Ingredient ingredient) {
		Object custom = customIngredient(ingredient);
		if (custom == null || !custom.getClass().getName()
			.equals("dev.latvian.mods.kubejs.platform.fabric.ingredient.KubeJSNbtIngredient")) return null;
		try {
			var field = custom.getClass().getDeclaredField("base");
			if (!field.trySetAccessible()) return null;
			Object base = field.get(custom);
			return base instanceof Ingredient value ? value : null;
		} catch (ReflectiveOperationException | SecurityException ignored) {
			return null;
		}
	}

	private static boolean isSameExplicitItem(JsonElement json, String itemId) {
		if (json == null || json.isJsonNull()) return false;
		if (json.isJsonArray()) {
			JsonArray array = json.getAsJsonArray();
			if (array.isEmpty()) return false;
			for (JsonElement child : array) {
				if (!isSameExplicitItem(child, itemId)) return false;
			}
			return true;
		}
		if (!json.isJsonObject()) return false;
		JsonObject object = json.getAsJsonObject();
		return object.size() == 1 && object.has("item") && object.get("item").isJsonPrimitive()
			&& object.get("item").getAsString().equals(itemId);
	}

	static String customIngredientClass(Ingredient ingredient) {
		Object custom = customIngredient(ingredient);
		return custom == null ? "" : custom.getClass().getName();
	}

	private static Object customIngredient(Ingredient ingredient) {
		try {
			return ingredient.getClass().getMethod("getCustomIngredient").invoke(ingredient);
		} catch (ReflectiveOperationException ignored) {
			return null;
		}
	}

	private static Object unwrap(Object value) {
		while (value instanceof Wrapper wrapper) value = wrapper.unwrap();
		return value;
	}
}
