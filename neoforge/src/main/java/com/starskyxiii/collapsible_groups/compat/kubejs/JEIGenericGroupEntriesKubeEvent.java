package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeArray;
import dev.latvian.mods.rhino.Wrapper;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Collects KubeJS RecipeViewerEvents.groupEntries() calls for a generic
 * JEI ingredient type T (anything other than item and fluid).
 */
public class JEIGenericGroupEntriesKubeEvent<T> implements dev.latvian.mods.kubejs.recipe.viewer.GroupEntriesKubeEvent {

	private final String typeId;
	private final List<ViewerIngredient<ITypedIngredient<?>>> allIngredients;
	private final List<GroupDefinition> collected = new ArrayList<>();

	public JEIGenericGroupEntriesKubeEvent(
		String typeId,
		List<ViewerIngredient<ITypedIngredient<?>>> allIngredients
	) {
		this.typeId = typeId;
		this.allIngredients = List.copyOf(allIngredients);
	}

	@Override
	public void group(Context cx, Object filter, Identifier groupId, Component description) {
		String id = "__kjs_" + typeId.replace(':', '_').replace('/', '_') + "_"
			+ groupId.toString().replace(':', '_').replace('/', '_');
		String name = description.getString();

		GroupFilter compiled = KubeJsFilterCompiler.compileGenericFilter(typeId, filter);
		if (compiled != null) {
			collected.add(new GroupDefinition(id, name, true, compiled));
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
			Identifier loc = ingredient.view().resourceLocation();
			if (loc != null) {
				nodes.add(KubeJsFilterLowering.lowerResolvedGenericIngredient(typeId, loc));
			}
		}

		GroupFilter lowered = KubeJsFilterLowering.composeFallbackNodes(new ArrayList<>(nodes));
		if (lowered != null) {
			collected.add(new GroupDefinition(id, name, true, lowered));
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
				Identifier loc = ingredient.view().resourceLocation();
				return loc != null && namespace.equals(loc.getNamespace());
			};
		}
		if (str.startsWith("#")) {
			Identifier tagId = Identifier.parse(str.substring(1));
			return ingredient -> ingredient.view().hasTag(tagId);
		}
		Identifier exactId = Identifier.tryParse(str);
		if (exactId == null) return ingredient -> false;
		return ingredient -> exactId.equals(ingredient.view().resourceLocation());
	}

	private static Object unwrap(Object filter) {
		while (filter instanceof Wrapper wrapper) {
			filter = wrapper.unwrap();
		}
		return filter;
	}

	public List<GroupDefinition> getCollected() {
		return List.copyOf(collected);
	}
}
