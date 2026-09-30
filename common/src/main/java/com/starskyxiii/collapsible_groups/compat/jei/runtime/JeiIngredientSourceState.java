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
import mezz.jei.api.runtime.IJeiRuntime;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JeiIngredientSourceState {
	private static final Object LOCK = new Object();
	private static volatile SourceToken source = new SourceToken(0, null, null);
	private static volatile List<ItemStack> allItems = List.of();
	private static volatile List<JeiFluidIngredient> allFluids = List.of();
	private static volatile EditorItemIndex editorItemIndex;

	private JeiIngredientSourceState() {}

	public static void activate(IJeiRuntime runtime) {
		IIngredientManager manager = runtime.getIngredientManager();
		synchronized (LOCK) {
			source = new SourceToken(source.revision() + 1, runtime, manager);
			allItems = List.of();
			allFluids = List.of();
			editorItemIndex = null;
		}
	}

	public static void deactivate() {
		synchronized (LOCK) {
			source = new SourceToken(source.revision() + 1, null, null);
			allItems = List.of();
			allFluids = List.of();
			editorItemIndex = null;
		}
	}

	static SourceToken capture(IIngredientManager manager) {
		SourceToken current = source;
		return current.runtime() != null && current.manager() == manager ? current : null;
	}

	static boolean install(SourceToken expected, List<ItemStack> items, List<?> fluids) {
		if (expected == null) return false;
		List<ItemStack> itemCopy = items == null ? null : List.copyOf(items);
		List<JeiFluidIngredient> fluidCopy = fluids == null ? null : fluids.stream()
			.map(value -> value instanceof JeiFluidIngredient jei ? jei
				: new JeiFluidIngredient(value, JeiIngredientTypes.convertFluid(value).require())).toList();
		synchronized (LOCK) {
			if (source != expected) return false;
			boolean replaceItems = itemCopy != null && !itemCopy.isEmpty() && allItems.isEmpty();
			boolean replaceFluids = fluidCopy != null && !fluidCopy.isEmpty() && allFluids.isEmpty();
			if (!replaceItems && !replaceFluids) return false;
			if (replaceItems) allItems = itemCopy;
			if (replaceFluids) allFluids = fluidCopy;
			source = new SourceToken(source.revision() + 1, source.runtime(), source.manager());
			editorItemIndex = null;
			return true;
		}
	}

	public static void populateIfEmpty() {
		SourceToken expected = source;
		if (expected.runtime() == null) return;
		IIngredientManager manager = expected.manager();
		List<ItemStack> items = allItems.isEmpty() ? new ArrayList<>(manager.getAllIngredients(VanillaTypes.ITEM_STACK)) : null;
		IIngredientType<?> fluidType = JeiIngredientTypes.getFluidType();
		List<?> fluids = fluidType != null && allFluids.isEmpty() ? new ArrayList<>(manager.getAllIngredients(fluidType)) : null;
		install(expected, items, fluids);
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

	public static List<ItemStack> items() { return allItems; }
	public static boolean itemsEmpty() { return allItems.isEmpty(); }
	public static void clearItems() {
		synchronized (LOCK) {
			source = new SourceToken(source.revision() + 1, source.runtime(), source.manager());
			allItems = List.of();
			editorItemIndex = null;
		}
	}
	public static List<Object> fluids() { return allFluids.stream().map(JeiFluidIngredient::viewerValue).toList(); }
	public static boolean fluidsEmpty() { return allFluids.isEmpty(); }
	public static void clearFluids() {
		synchronized (LOCK) {
			source = new SourceToken(source.revision() + 1, source.runtime(), source.manager());
			allFluids = List.of();
			editorItemIndex = null;
		}
	}

	public static void warmEditorIndex() {
		editorIndex(EditorItemIndex::build);
	}

	public static List<ItemStack> resolveDraft(GroupFilterEditorDraft draft, boolean enabled) {
		if (!enabled || draft.explicitItemSelectors().isEmpty() && draft.itemTags().isEmpty()) return List.of();
		populateIfEmpty();
		EditorItemIndex index = editorIndex(EditorItemIndex::build);
		return index == null ? List.of() : index.resolveDraft(draft);
	}

	public static List<ItemStack> resolveHybridDraft(GroupFilterEditorDraft draft, boolean enabled) {
		if (!enabled) return List.of();
		populateIfEmpty();
		EditorItemIndex index = editorIndex(EditorItemIndex::build);
		return index == null ? List.of() : index.resolveHybridDraft(draft, JeiIngredientSourceState::resolvePreserved);
	}

	static EditorItemIndex editorIndex(Function<List<ItemStack>, EditorItemIndex> build) {
		SourceToken expected;
		List<ItemStack> items;
		synchronized (LOCK) {
			if (source.runtime() == null || allItems.isEmpty()) return null;
			if (editorItemIndex != null) return editorItemIndex;
			expected = source;
			items = allItems;
		}
		EditorItemIndex built = build.apply(items);
		synchronized (LOCK) {
			if (source == expected && editorItemIndex == null) editorItemIndex = built;
			return editorItemIndex;
		}
	}

	static record SourceToken(long revision, IJeiRuntime runtime, IIngredientManager manager) {}

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
