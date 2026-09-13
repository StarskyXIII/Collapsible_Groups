package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientTypes;
import com.starskyxiii.collapsible_groups.compat.jei.JeiFluidIngredient;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JeiIngredientSourceState {
	private static volatile List<ItemStack> allItems = List.of();
	private static volatile List<JeiFluidIngredient> allFluids = List.of();
	private static volatile EditorItemIndex editorItemIndex;

	private JeiIngredientSourceState() {}

	public static void populateIfEmpty() {
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null) return;
		IIngredientManager manager = runtime.getIngredientManager();
		if (allItems.isEmpty()) setItems(new ArrayList<>(manager.getAllIngredients(VanillaTypes.ITEM_STACK)));
		IIngredientType<?> fluidType = JeiIngredientTypes.getFluidType();
		if (fluidType != null && allFluids.isEmpty()) {
			setFluids(new ArrayList<>(manager.getAllIngredients(fluidType)));
		}
	}

	public static List<ItemStack> resolveItems(GroupDefinition group) {
		populateIfEmpty();
		return !allItems.isEmpty()
			? allItems.stream().filter(group::matches).toList()
			: BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(group::matches).toList();
	}

	public static List<Object> resolveFluids(GroupDefinition group) {
		populateIfEmpty();
		return !allFluids.isEmpty()
			? allFluids.stream().filter(fluid -> GroupMatcher.matchesFluid(group, fluid.fluid()))
				.map(JeiFluidIngredient::viewerValue).toList()
			: List.of();
	}

	public static List<GenericIngredientRef> resolveGeneric(GroupDefinition group) {
		if (!group.hasGenericFilters()) return List.of();
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null) return List.of();
		IIngredientManager manager = runtime.getIngredientManager();
		List<GenericIngredientRef> result = new ArrayList<>();
		for (Map.Entry<String, IIngredientType<?>> entry : JeiIngredientTypes.getAll().entrySet()) {
			appendMatching(group, entry.getKey(), entry.getValue(), manager, result);
		}
		return List.copyOf(result);
	}

	public static List<GenericIngredientRef> allGeneric() {
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null) return List.of();
		IIngredientManager manager = runtime.getIngredientManager();
		List<GenericIngredientRef> result = new ArrayList<>();
		for (Map.Entry<String, IIngredientType<?>> entry : JeiIngredientTypes.getAll().entrySet()) {
			appendAll(entry.getKey(), entry.getValue(), manager, result);
		}
		return List.copyOf(result);
	}

	public static void setItems(List<ItemStack> items) {
		allItems = List.copyOf(items);
		editorItemIndex = null;
	}

	public static List<ItemStack> items() { return allItems; }
	public static boolean itemsEmpty() { return allItems.isEmpty(); }
	public static void clearItems() { allItems = List.of(); editorItemIndex = null; }
	public static void setFluids(List<?> fluids) {
		allFluids = fluids.stream().map(value -> value instanceof JeiFluidIngredient jei ? jei
			: new JeiFluidIngredient(value, JeiIngredientTypes.convertFluid(value).require())).toList();
	}
	public static List<Object> fluids() { return allFluids.stream().map(JeiFluidIngredient::viewerValue).toList(); }
	public static boolean fluidsEmpty() { return allFluids.isEmpty(); }
	public static void clearFluids() { allFluids = List.of(); }

	public static void warmEditorIndex() {
		populateIfEmpty();
		editorIndex();
	}

	public static List<ItemStack> resolveDraft(GroupFilterEditorDraft draft, boolean enabled) {
		if (!enabled || draft.explicitItemSelectors().isEmpty() && draft.itemTags().isEmpty()) return List.of();
		populateIfEmpty();
		return editorIndex().resolveDraft(draft);
	}

	public static List<ItemStack> resolveHybridDraft(GroupFilterEditorDraft draft, boolean enabled) {
		if (!enabled) return List.of();
		populateIfEmpty();
		return editorIndex().resolveHybridDraft(draft, JeiIngredientSourceState::resolvePreserved);
	}

	private static EditorItemIndex editorIndex() {
		EditorItemIndex index = editorItemIndex;
		if (index != null) return index;
		synchronized (JeiIngredientSourceState.class) {
			if (editorItemIndex == null) editorItemIndex = EditorItemIndex.build(allItems);
			return editorItemIndex;
		}
	}

	private static List<ItemStack> resolvePreserved(List<GroupFilter> preserved) {
		if (preserved.isEmpty()) return List.of();
		GroupFilter combined = preserved.size() == 1
			? preserved.get(0)
			: Filters.any(preserved.toArray(GroupFilter[]::new));
		try {
			return resolveItems(new GroupDefinition("__cg_preserved_preview__", "", true, combined));
		} catch (IllegalArgumentException ignored) {
			return List.of();
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> void appendMatching(GroupDefinition group, String typeId,
		IIngredientType<?> rawType, IIngredientManager manager, List<GenericIngredientRef> out) {
		IIngredientType<T> type = (IIngredientType<T>) rawType;
		IIngredientHelper<T> helper = manager.getIngredientHelper(type);
		for (T ingredient : manager.getAllIngredients(type)) {
			if (GroupMatcher.matchesGeneric(group, typeId, ingredient, helper)) {
				out.add(new GenericIngredientRef(typeId, (IIngredientType<Object>) type, ingredient));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> void appendAll(String typeId, IIngredientType<?> rawType,
		IIngredientManager manager, List<GenericIngredientRef> out) {
		IIngredientType<T> type = (IIngredientType<T>) rawType;
		for (T ingredient : manager.getAllIngredients(type)) {
			out.add(new GenericIngredientRef(typeId, (IIngredientType<Object>) type, ingredient));
		}
	}
}
