package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsFilterComposition;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.KubeJsItemFilterLowering;
import dev.latvian.mods.kubejs.core.IngredientSupplierKJS;
import dev.latvian.mods.kubejs.fluid.NamespaceFluidIngredient;
import dev.latvian.mods.kubejs.fluid.RegExFluidIngredient;
import dev.latvian.mods.kubejs.ingredient.CreativeTabIngredient;
import dev.latvian.mods.kubejs.ingredient.NamespaceIngredient;
import dev.latvian.mods.kubejs.ingredient.RegExIngredient;
import dev.latvian.mods.kubejs.ingredient.WildcardIngredient;
import dev.latvian.mods.kubejs.util.ListJS;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.regexp.NativeRegExp;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IntersectionIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.CompoundFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.DifferenceFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.IntersectionFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SingleFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.TagFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Set;

public final class KubeJsFilterCompiler {
	private KubeJsFilterCompiler() {}

	public static @Nullable GroupFilter compileItemFilter(Context cx, Object filter) {
		try {
			return compileItemFilterUnchecked(cx, filter);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static @Nullable GroupFilter compileItemFilterUnchecked(Context cx, Object filter) {
		filter = unwrap(filter);

		if (filter == null || isRegexLike(filter) || filter instanceof BaseFunction) {
			return null;
		}

		if (filter instanceof GroupFilter groupFilter) {
			return KubeJsFilterComposition.supportsTree(groupFilter) ? groupFilter : null;
		}

		List<?> list = ListJS.of(filter);
		if (list != null) {
			return compileItemList(cx, list);
		}

		if (filter instanceof Map<?, ?> map) {
			if (!isValidItemObject(map)) {
				return null;
			}
			return compileItemObject(map);
		}

		if (filter instanceof ItemStack stack) {
			return KubeJsItemFilterLowering.lowerResolvedStack(stack);
		}

		if (filter instanceof ItemLike itemLike) {
			return Filters.itemId(BuiltInRegistries.ITEM.getKey(itemLike.asItem()).toString());
		}

		if (filter instanceof Ingredient ingredient) {
			return compileItemFilter(ingredient);
		}

		if (filter instanceof SizedIngredient || filter instanceof IngredientSupplierKJS) return null;

		if (filter instanceof TagKey<?> tag) {
			if (tag.registry().equals(Registries.ITEM)) return Filters.itemTag(tag.location().toString());
			if (tag.registry().equals(Registries.BLOCK)) return Filters.blockTag(tag.location().toString());
			return null;
		}

		if (filter instanceof CharSequence str) {
			return compileItemString(cx, str.toString());
		}

		return null;
	}

	public static @Nullable GroupFilter compileItemFilter(Ingredient ingredient) {
		try {
			return compileItemIngredientUnchecked(ingredient);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static @Nullable GroupFilter compileItemIngredientUnchecked(Ingredient ingredient) {
		if (ingredient.isEmpty()) {
			return null;
		}

		if (ingredient.isCustom()) {
			return compileCustomItemIngredient(ingredient.getCustomIngredient());
		}

		TagKey<Item> tag = KubeJSCompatibility.tagKeyOf(ingredient);
		if (tag != null) {
			return Filters.itemTag(tag.location().toString());
		}

		if (KubeJSCompatibility.containsAnyTag(ingredient)) {
			return null;
		}

		return lowerExplicitItemStacks(List.of(ingredient.getItems()));
	}

	public static @Nullable GroupFilter compileFluidFilter(Context cx, Object filter) {
		try {
			return compileFluidFilterUnchecked(cx, filter);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static @Nullable GroupFilter compileFluidFilterUnchecked(Context cx, Object filter) {
		filter = unwrap(filter);

		if (filter == null || isRegexLike(filter) || filter instanceof BaseFunction) {
			return null;
		}

		List<?> list = ListJS.of(filter);
		if (list != null) {
			return compileFluidList(cx, list);
		}

		if (filter instanceof FluidIngredient ingredient) {
			return compileFluidFilter(ingredient);
		}

		if (filter instanceof FluidStack) {
			return null;
		}

		if (filter instanceof SizedFluidIngredient) return null;

		if (filter instanceof Fluid fluid) {
			return Filters.fluidId(BuiltInRegistries.FLUID.getKey(fluid).toString());
		}

		if (filter instanceof CharSequence str) {
			return compileFluidString(cx, str.toString());
		}

		if (filter instanceof TagKey<?> tag && tag.registry().equals(Registries.FLUID)) {
			return Filters.fluidTag(tag.location().toString());
		}

		return null;
	}

	public static @Nullable GroupFilter compileFluidFilter(FluidIngredient ingredient) {
		try {
			return compileFluidIngredientUnchecked(ingredient);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static @Nullable GroupFilter compileFluidIngredientUnchecked(FluidIngredient ingredient) {
		if (ingredient.isEmpty()) {
			return null;
		}

		return switch (ingredient) {
			case CompoundFluidIngredient compound -> compileFluidChildren(compound.children(), true);
			case IntersectionFluidIngredient intersection -> compileFluidChildren(intersection.children(), false);
			case DifferenceFluidIngredient difference -> compileDifferenceFluid(difference);
			case TagFluidIngredient tag -> Filters.fluidTag(tag.tag().location().toString());
			case NamespaceFluidIngredient namespace -> Filters.fluidNamespace(namespace.namespace);
			case SingleFluidIngredient single -> Filters.fluidId(BuiltInRegistries.FLUID.getKey(single.fluid().value()).toString());
			case DataComponentFluidIngredient ignored -> null;
			case RegExFluidIngredient ignored -> null;
			default -> null;
		};
	}

	public static @Nullable GroupFilter compileGenericFilter(String typeId, Object filter) {
		filter = unwrap(filter);

		if (filter == null || filter instanceof BaseFunction || isRegexLike(filter)) {
			return null;
		}

		if (filter instanceof CharSequence str) {
			return compileGenericString(typeId, str.toString());
		}

		List<?> list = ListJS.of(filter);
		if (list != null) {
			List<GroupFilter> children = new ArrayList<>();
			for (Object element : list) {
				GroupFilter child = compileGenericFilter(typeId, element);
				if (child == null) {
					return null;
				}
				children.add(child);
			}
			return KubeJsFilterComposition.any(children);
		}

		return null;
	}

	private static @Nullable GroupFilter compileItemString(Context cx, String input) {
		String trimmed = input.trim();
		if (trimmed.isEmpty()
			|| "-".equals(trimmed)
			|| "*".equals(trimmed)
			|| trimmed.startsWith("%")
			|| looksLikeRegexString(trimmed)) {
			return null;
		}

		if (trimmed.startsWith("block:#")) {
			String blockTag = trimmed.substring("block:#".length());
			if (ResourceLocation.tryParse(blockTag) == null) {
				return null;
			}
			return Filters.blockTag(blockTag);
		}

		return compileItemFilter(KubeJSCompatibility.wrapItem(cx, trimmed));
	}

	private static @Nullable GroupFilter compileItemObject(Map<?, ?> map) {
		List<GroupFilter> children = new ArrayList<>(7);

		addIfPresent(children, compileItemPathStartsWith(map));
		addIfPresent(children, compileItemPathContains(map));
		addIfPresent(children, compileItemPathEndsWith(map));
		addIfPresent(children, compileItemNamespace(map));
		addIfPresent(children, compileItemId(map));
		addIfPresent(children, compileItemTag(map));
		addIfPresent(children, compileBlockTag(map));

		if (children.isEmpty()) {
			return null;
		}
		if (children.size() == 1) {
			return children.get(0);
		}
		return KubeJsFilterComposition.all(children);
	}

	private static @Nullable GroupFilter compileFluidString(Context cx, String input) {
		String trimmed = input.trim();
		if (trimmed.isEmpty()
			|| "-".equals(trimmed)
			|| "empty".equals(trimmed)
			|| "minecraft:empty".equals(trimmed)
			|| looksLikeRegexString(trimmed)) {
			return null;
		}

		return compileFluidFilter(KubeJSCompatibility.wrapFluid(cx, trimmed));
	}

	private static @Nullable GroupFilter compileItemList(Context cx, List<?> list) {
		List<GroupFilter> children = new ArrayList<>();
		for (Object element : list) {
			GroupFilter child = compileItemFilter(cx, element);
			if (child == null) {
				return null;
			}
			children.add(child);
		}
		return KubeJsFilterComposition.any(children);
	}

	private static @Nullable GroupFilter compileFluidList(Context cx, List<?> list) {
		List<GroupFilter> children = new ArrayList<>();
		for (Object element : list) {
			GroupFilter child = compileFluidFilter(cx, element);
			if (child == null) {
				return null;
			}
			children.add(child);
		}
		return KubeJsFilterComposition.any(children);
	}

	private static @Nullable GroupFilter compileCustomItemIngredient(ICustomIngredient ingredient) {
		return switch (ingredient) {
			case CompoundIngredient compound -> compileItemChildren(compound.children(), true);
			case IntersectionIngredient intersection -> compileItemChildren(intersection.children(), false);
			case DifferenceIngredient difference -> compileDifferenceItem(difference);
			case NamespaceIngredient namespace -> Filters.itemNamespace(namespace.namespace());
			case DataComponentIngredient data -> compileDataComponentIngredient(data);
			case RegExIngredient ignored -> null;
			case CreativeTabIngredient ignored -> null;
			case WildcardIngredient ignored -> null;
			default -> null;
		};
	}

	private static @Nullable GroupFilter compileDataComponentIngredient(DataComponentIngredient data) {
		if (!data.isStrict()) return null;
		List<GroupFilter> stacks = data.getItems().map(Filters::exactStack).toList();
		return KubeJsFilterComposition.any(stacks);
	}

	private static @Nullable GroupFilter compileDifferenceItem(DifferenceIngredient difference) {
		GroupFilter base = compileItemFilter(difference.base());
		GroupFilter subtracted = compileItemFilter(difference.subtracted());
		if (base == null || subtracted == null) {
			return null;
		}
		return KubeJsFilterComposition.all(List.of(base, Filters.not(subtracted)));
	}

	private static @Nullable GroupFilter compileDifferenceFluid(DifferenceFluidIngredient difference) {
		GroupFilter base = compileFluidFilter(difference.base());
		GroupFilter subtracted = compileFluidFilter(difference.subtracted());
		if (base == null || subtracted == null) {
			return null;
		}
		return KubeJsFilterComposition.all(List.of(base, Filters.not(subtracted)));
	}

	private static @Nullable GroupFilter compileItemChildren(List<Ingredient> children, boolean any) {
		List<GroupFilter> compiled = new ArrayList<>();
		for (Ingredient child : children) {
			GroupFilter filter = compileItemFilter(child);
			if (filter == null) {
				return null;
			}
			compiled.add(filter);
		}
		return any
			? KubeJsFilterComposition.any(compiled)
			: compileAllChildren(compiled);
	}

	private static @Nullable GroupFilter compileFluidChildren(List<FluidIngredient> children, boolean any) {
		List<GroupFilter> compiled = new ArrayList<>();
		for (FluidIngredient child : children) {
			GroupFilter filter = compileFluidFilter(child);
			if (filter == null) {
				return null;
			}
			compiled.add(filter);
		}
		return any
			? KubeJsFilterComposition.any(compiled)
			: compileAllChildren(compiled);
	}

	private static @Nullable GroupFilter compileAllChildren(List<GroupFilter> children) {
		if (children.isEmpty()) {
			return null;
		}
		if (children.size() == 1) {
			return children.get(0);
		}
		return KubeJsFilterComposition.all(children);
	}

	private static @Nullable GroupFilter lowerExplicitItemStacks(List<ItemStack> stacks) {
		List<GroupFilter> children = new ArrayList<>();
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			children.add(Filters.itemId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
		}
		return KubeJsFilterComposition.any(children);
	}

	private static @Nullable GroupFilter compileGenericString(String typeId, String input) {
		String trimmed = input.trim();
		if (trimmed.isEmpty()
			|| "-".equals(trimmed)
			|| "*".equals(trimmed)
			|| looksLikeRegexString(trimmed)) {
			return null;
		}
		if (trimmed.startsWith("@")) {
			String namespace = trimmed.substring(1);
			return ResourceLocation.isValidNamespace(namespace) ? Filters.genericNamespace(typeId, namespace) : null;
		}
		if (trimmed.startsWith("#")) {
			String tag = trimmed.substring(1);
			return ResourceLocation.tryParse(tag) != null ? Filters.genericTag(typeId, tag) : null;
		}
		return ResourceLocation.tryParse(trimmed) != null ? Filters.genericId(typeId, trimmed) : null;
	}

	private static Object unwrap(Object filter) {
		while (filter instanceof Wrapper wrapper) {
			filter = wrapper.unwrap();
		}
		return filter;
	}

	private static @Nullable GroupFilter compileItemPathStartsWith(Map<?, ?> map) {
		String prefix = stringProperty(map, "itemPathStartsWith");
		if (prefix == null) {
			return null;
		}
		prefix = prefix.trim();
		return prefix.isEmpty() ? null : Filters.itemPathStartsWith(prefix);
	}

	private static @Nullable GroupFilter compileItemPathContains(Map<?, ?> map) {
		String needle = stringProperty(map, "itemPathContains");
		if (needle == null) {
			return null;
		}
		needle = needle.trim();
		return needle.isEmpty() ? null : Filters.itemPathContains(needle);
	}

	private static @Nullable GroupFilter compileItemPathEndsWith(Map<?, ?> map) {
		String suffix = stringProperty(map, "itemPathEndsWith");
		if (suffix == null) {
			return null;
		}
		suffix = suffix.trim();
		return suffix.isEmpty() ? null : Filters.itemPathEndsWith(suffix);
	}

	private static @Nullable GroupFilter compileItemNamespace(Map<?, ?> map) {
		String namespace = stringProperty(map, "itemNamespace");
		if (namespace == null) {
			return null;
		}
		namespace = namespace.trim();
		return namespace.isEmpty() ? null : Filters.itemNamespace(namespace);
	}

	private static @Nullable GroupFilter compileItemId(Map<?, ?> map) {
		String id = stringProperty(map, "itemId");
		if (id == null) {
			return null;
		}
		id = id.trim();
		return isValidResourceLocation(id) ? Filters.itemId(id) : null;
	}

	private static @Nullable GroupFilter compileItemTag(Map<?, ?> map) {
		String tag = stringProperty(map, "itemTag");
		if (tag == null) {
			return null;
		}
		tag = tag.trim();
		return isValidResourceLocation(tag) ? Filters.itemTag(tag) : null;
	}

	private static @Nullable GroupFilter compileBlockTag(Map<?, ?> map) {
		String tag = stringProperty(map, "blockTag");
		if (tag == null) {
			return null;
		}
		tag = tag.trim();
		return isValidResourceLocation(tag) ? Filters.blockTag(tag) : null;
	}

	private static void addIfPresent(List<GroupFilter> children, @Nullable GroupFilter filter) {
		if (filter != null) {
			children.add(filter);
		}
	}

	private static @Nullable String stringProperty(Map<?, ?> map, String key) {
		Object value = map.get(key);
		return value instanceof CharSequence chars ? chars.toString() : null;
	}

	private static boolean isValidResourceLocation(String value) {
		return !value.isEmpty() && ResourceLocation.tryParse(value) != null;
	}

	private static boolean isRegexLike(Object filter) {
		return filter instanceof Pattern || filter instanceof NativeRegExp;
	}

	private static boolean looksLikeRegexString(String input) {
		return input.length() > 1 && input.startsWith("/") && input.endsWith("/");
	}

	private static boolean isValidItemObject(Map<?, ?> map) {
		if (map.isEmpty() || !KNOWN_ITEM_OBJECT_KEYS.containsAll(map.keySet())) return false;
		for (var entry : map.entrySet()) {
			if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof CharSequence chars)) return false;
			String value = chars.toString().trim();
			if (value.isEmpty()) return false;
			if ((key.equals("itemId") || key.equals("itemTag") || key.equals("blockTag"))
				&& ResourceLocation.tryParse(value) == null) return false;
		}
		return true;
	}

	public static boolean isMaterializableItemIdSet(Object filter) {
		try {
			filter = unwrap(filter);
			if (isRegexLike(filter)) return true;
			if (filter instanceof CharSequence chars) return looksLikeRegexString(chars.toString().trim());
			if (filter instanceof Ingredient ingredient && ingredient.isCustom()) {
				return ingredient.getCustomIngredient() instanceof RegExIngredient
					|| ingredient.getCustomIngredient() instanceof WildcardIngredient;
			}
			return false;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	public static boolean isMaterializableFluidIdSet(Object filter) {
		try {
			filter = unwrap(filter);
			if (isRegexLike(filter)) return true;
			if (filter instanceof CharSequence chars) return looksLikeRegexString(chars.toString().trim());
			return filter instanceof RegExFluidIngredient;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static final Set<Object> KNOWN_ITEM_OBJECT_KEYS = Set.of(
		"itemPathStartsWith", "itemPathContains", "itemPathEndsWith", "itemNamespace",
		"itemId", "itemTag", "blockTag"
	);
}
