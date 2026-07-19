package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsFilterComposition;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupCollector;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupIds;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweredGroup;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeArray;
import dev.latvian.mods.rhino.Wrapper;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Collects KubeJS RecipeViewerEvents.groupEntries() calls for a generic
 * JEI ingredient type T (anything other than item and fluid).
 */
public class JEIGenericGroupEntriesKubeEvent<T> implements dev.latvian.mods.kubejs.recipe.viewer.GroupEntriesKubeEvent, KubeJsGroupCollector {

	private final String typeId;
	private final List<ViewerIngredient<ITypedIngredient<?>>> allIngredients;
	private final List<KubeJsLoweredGroup> collected = new ArrayList<>();

	public JEIGenericGroupEntriesKubeEvent(
		String typeId,
		List<ViewerIngredient<ITypedIngredient<?>>> allIngredients
	) {
		this.typeId = typeId;
		this.allIngredients = List.copyOf(allIngredients);
	}

	@Override
	public void group(Context cx, Object filter, ResourceLocation groupId, Component description) {
		String id = KubeJsGroupIds.generic(typeId, groupId.toString());
		String name = description.getString();

		GroupFilter compiled = KubeJsFilterCompiler.compileGenericFilter(typeId, filter);
		if (compiled != null && KubeJsFilterComposition.supportsTree(compiled)) {
			collected.add(new KubeJsLoweredGroup(id, name, compiled));
			return;
		}

		Object unwrapped = unwrap(filter);
		if (unwrapped instanceof BaseFunction) {
			throw new UnsupportedOperationException(
				"JS function filters are not supported for ingredient type '" + typeId + "'. " +
				"Use '@modid', '#tag:id', 'exact:id', or a string array instead."
			);
		}

		Predicate<ViewerIngredient<ITypedIngredient<?>>> predicate = buildPredicate(unwrapped);
		LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
		for (ViewerIngredient<ITypedIngredient<?>> ingredient : allIngredients) {
			if (!predicate.test(ingredient)) {
				continue;
			}
			ResourceLocation loc = ingredient.view().resourceLocation();
			if (loc != null) {
				nodes.add(KubeJsFilterLowering.lowerResolvedGenericIngredient(typeId, loc));
			}
		}

		GroupFilter lowered = KubeJsFilterComposition.any(new ArrayList<>(nodes));
		if (lowered != null) {
			collected.add(new KubeJsLoweredGroup(id, name, lowered));
		}
	}

	private Predicate<ViewerIngredient<ITypedIngredient<?>>> buildPredicate(Object filter) {
		if (filter instanceof String str) {
			return buildStringPredicate(str);
		}
		if (filter instanceof NativeArray arr) {
			List<Predicate<ViewerIngredient<ITypedIngredient<?>>>> predicates = new ArrayList<>();
			for (Object item : arr) {
				predicates.add(buildStringPredicate(String.valueOf(item)));
			}
			return ingredient -> predicates.stream().anyMatch(p -> p.test(ingredient));
		}
		if (filter instanceof List<?> list) {
			List<Predicate<ViewerIngredient<ITypedIngredient<?>>>> predicates = new ArrayList<>();
			for (Object item : list) {
				predicates.add(buildStringPredicate(String.valueOf(item)));
			}
			return ingredient -> predicates.stream().anyMatch(p -> p.test(ingredient));
		}
		return ingredient -> false;
	}

	private Predicate<ViewerIngredient<ITypedIngredient<?>>> buildStringPredicate(String str) {
		if (str.startsWith("@")) {
			String namespace = str.substring(1);
			return ingredient -> {
				ResourceLocation loc = ingredient.view().resourceLocation();
				return loc != null && namespace.equals(loc.getNamespace());
			};
		}
		if (str.startsWith("#")) {
			ResourceLocation tagId = ResourceLocation.parse(str.substring(1));
			return ingredient -> ingredient.view().hasTag(tagId);
		}
		ResourceLocation exactId = ResourceLocation.tryParse(str);
		if (exactId == null) return ingredient -> false;
		return ingredient -> exactId.equals(ingredient.view().resourceLocation());
	}

	private static Object unwrap(Object filter) {
		while (filter instanceof Wrapper wrapper) {
			filter = wrapper.unwrap();
		}
		return filter;
	}

	@Override
	public List<KubeJsLoweredGroup> collectedGroups() {
		return List.copyOf(collected);
	}
}
