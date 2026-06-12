package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.api.IngredientTypeRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.element.FluidChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GenericChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupHeaderElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupMatcher;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.IngredientFilterHelper;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.SearchUngroupPolicy;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.ingredients.TypedIngredient;
import mezz.jei.gui.filter.IFilterTextSource;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(value = IngredientFilter.class, remap = false)
public abstract class MixinIngredientFilter {
	@Shadow @Nullable private List<IElement<?>> ingredientListCached;
	@Shadow @Final  private IFilterTextSource filterTextSource;
	@Shadow @Final  private IIngredientManager ingredientManager;

	@org.spongepowered.asm.mixin.gen.Invoker("getIngredientListUncached")
	protected abstract Stream<ITypedIngredient<?>> cg$getIngredientListUncached(String filterText);

	@org.spongepowered.asm.mixin.gen.Invoker("notifyListenersOfChange")
	protected abstract void cg$notifyListenersOfChange();

	@Unique @Nullable private Map<ITypedIngredient<?>, GroupDefinition> cg$ingredientGroupIndex = null;
	@Unique @Nullable private List<IElement<?>>                         cg$baseList             = null;
	@Unique @Nullable private List<String>                              cg$baseListGroupIds     = null;
	@Unique @Nullable private Map<String, List<IElement<?>>>            cg$childrenByGroupId    = null;
	@Unique @Nullable private Set<String>                               cg$enabledGroupIds      = null;
	@Unique @Nullable private List<ITypedIngredient<?>>                 cg$cachedFullList       = null;
	@Unique @Nullable private CompletableFuture<Map<ITypedIngredient<?>, GroupDefinition>> cg$pendingIndex = null;
	@Unique private boolean cg$searchActiveForCache = false;

	@Unique private static final ExecutorService REBUILD_EXECUTOR =
		Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "CG-IndexRebuild"); t.setDaemon(true); return t; });

	@Inject(method = "<init>", at = @At("TAIL"))
	private void cg$onInit(CallbackInfo ci) {
		GroupRegistry.jeiInvalidateCallback = this::cg$invalidateAndNotify;
		GroupRegistry.jeiStructureInvalidateCallback = this::cg$invalidateStructureAndNotify;
		GroupRegistry.clearJeiAllItems();
		GroupRegistry.clearJeiAllFluids();
		GroupRegistry.clearKubeJsGroups();
		this.cg$cachedFullList = null;
	}

	@Inject(method = "getElements", at = @At("HEAD"), cancellable = true)
	private void cg$onGetElements(CallbackInfoReturnable<List<IElement<?>>> cir) {
		if (this.ingredientListCached == null) {
			String rawFilterText = this.filterTextSource.getFilterText();
			String filterText = rawFilterText.toLowerCase(Locale.ROOT);
			this.cg$searchActiveForCache = !rawFilterText.isBlank();
			List<ITypedIngredient<?>> ingredients = this.cg$getIngredientListUncached(filterText).toList();
			this.cg$buildStructureCache(ingredients);
			this.ingredientListCached = this.cg$buildDisplayFromCache();
		}
		cir.setReturnValue(this.ingredientListCached);
	}

	@Unique
	private void cg$invalidateAndNotify() {
		this.cg$clearStructureCaches();
		List<ITypedIngredient<?>> snapshot = this.cg$cachedFullList;
		if (snapshot != null) {
			CompletableFuture<Map<ITypedIngredient<?>, GroupDefinition>> future = CompletableFuture.supplyAsync(
				() -> this.cg$buildIngredientGroupIndex(snapshot), REBUILD_EXECUTOR);
			this.cg$pendingIndex = future;
			future.thenRunAsync(() -> {
				if (this.cg$pendingIndex != future) return;
				this.cg$clearStructureCaches();
				this.cg$notifyListenersOfChange();
			}, Minecraft.getInstance()::execute);
		} else {
			this.cg$pendingIndex = null;
			this.cg$ingredientGroupIndex = null;
		}
		this.cg$notifyListenersOfChange();
	}

	@Unique
	private void cg$clearStructureCaches() {
		this.ingredientListCached = null;
		this.cg$baseList = null;
		this.cg$baseListGroupIds = null;
		this.cg$childrenByGroupId = null;
		this.cg$enabledGroupIds = null;
		this.cg$searchActiveForCache = false;
	}

	@Unique
	private void cg$invalidateStructureAndNotify() {
		this.cg$clearStructureCaches();
		this.cg$notifyListenersOfChange();
	}

	@Unique
	private void cg$toggleAndRebuildDisplay() {
		if (this.cg$baseList != null) {
			this.ingredientListCached = this.cg$buildDisplayFromCache();
		} else {
			this.ingredientListCached = null;
		}
		this.cg$notifyListenersOfChange();
	}

	/** Level-1: item/fluid/generic index. */
	@Unique
	private Map<ITypedIngredient<?>, GroupDefinition> cg$buildIngredientGroupIndex(List<ITypedIngredient<?>> all) {
		Map<ITypedIngredient<?>, GroupDefinition> index = IngredientFilterHelper.buildItemGroupIndex(all);
		Map<String, List<Object>> fluidsByGroup = new HashMap<>();
		Map<String, List<Object>> fullMatchFluidsByGroup = new HashMap<>();
		Map<String, List<GenericIngredientRef>> fullMatchGenericByGroup = new HashMap<>();
		IIngredientType<?> fluidType = Services.PLATFORM.getJeiFluidType();
		List<GroupDefinition> allGroups = GroupRegistry.getAllIncludingKubeJs();
		List<GroupDefinition> fluidGroups = allGroups.stream()
			.filter(GroupDefinition::hasFluidFilters)
			.toList();
		List<GroupDefinition> genericGroups = allGroups.stream()
			.filter(GroupDefinition::hasGenericFilters)
			.toList();

		for (ITypedIngredient<?> ingredient : all) {
			if (ingredient.getItemStack().isPresent()) continue;
			boolean isFluid = false;
			if (fluidType != null && !fluidGroups.isEmpty()) {
				var fluidOpt = ingredient.getIngredient(fluidType);
				if (fluidOpt.isPresent()) {
					isFluid = true;
					Object fluid = fluidOpt.get();
					GroupDefinition firstMatch = null;
					for (GroupDefinition group : fluidGroups) {
						if (!GroupMatcher.matchesFluidIgnoringEnabled(group, fluid)) continue;
						if (firstMatch == null) {
							firstMatch = group;
							index.put(ingredient, group);
							fluidsByGroup.computeIfAbsent(group.id(), k -> new ArrayList<>()).add(fluid);
						}
						fullMatchFluidsByGroup.computeIfAbsent(group.id(), k -> new ArrayList<>()).add(fluid);
					}
				}
			}
			if (!isFluid && !genericGroups.isEmpty()) {
				cg$indexGenericIngredient(ingredient, index, genericGroups, fullMatchGenericByGroup);
			}
		}

		for (GroupDefinition group : allGroups) {
			fluidsByGroup.putIfAbsent(group.id(), List.of());
			fullMatchFluidsByGroup.putIfAbsent(group.id(), List.of());
			fullMatchGenericByGroup.putIfAbsent(group.id(), List.of());
		}
		GroupRegistry.setResolvedFluidsByGroup(fluidsByGroup);
		GroupRegistry.setFullMatchFluidsByGroup(fullMatchFluidsByGroup);
		GroupRegistry.setFullMatchGenericByGroup(fullMatchGenericByGroup);

		Map<String, Set<String>> fluidIdToGroupIds = new HashMap<>();
		for (var fluidEntry : fluidsByGroup.entrySet()) {
			String groupId = fluidEntry.getKey();
			for (Object fluid : fluidEntry.getValue()) {
				String registryId = Services.PLATFORM.getFluidId(fluid);
				fluidIdToGroupIds.computeIfAbsent(registryId, k -> new HashSet<>()).add(groupId);
			}
		}
		GroupRegistry.setFluidIdToGroupIds(fluidIdToGroupIds);
		return index;
	}

	@Unique
	@SuppressWarnings("unchecked")
	private <T> void cg$indexGenericIngredient(
		ITypedIngredient<?> typed,
		Map<ITypedIngredient<?>, GroupDefinition> index,
		List<GroupDefinition> genericGroups,
		Map<String, List<GenericIngredientRef>> fullMatchGenericByGroup
	) {
		for (Map.Entry<String, IIngredientType<?>> entry : IngredientTypeRegistry.getAll().entrySet()) {
			IIngredientType<T> type = (IIngredientType<T>) entry.getValue();
			ITypedIngredient<T> cast = typed.cast(type);
			if (cast == null) continue;
			IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(type);
			T ingredient = cast.getIngredient();
			GroupDefinition firstMatch = null;
			for (GroupDefinition group : genericGroups) {
				if (!GroupMatcher.matchesGenericIgnoringEnabled(group, entry.getKey(), ingredient, helper)) continue;
				if (firstMatch == null) {
					firstMatch = group;
					index.put(typed, group);
				}
				fullMatchGenericByGroup.computeIfAbsent(group.id(), k -> new ArrayList<>())
					.add(new GenericIngredientRef(entry.getKey(), (IIngredientType<Object>) type, ingredient));
			}
			return;
		}
	}

	/** Level-2: structure cache - item, fluid, and generic groups. */
	@Unique
	private void cg$buildStructureCache(List<ITypedIngredient<?>> ingredients) {
		List<ITypedIngredient<?>> all = this.cg$cachedFullList;
		IIngredientType<?> fluidType = Services.PLATFORM.getJeiFluidType();
		if (GroupRegistry.isJeiAllItemsEmpty()) {
			if (all == null) all = this.cg$getIngredientListUncached("").toList();
			this.cg$cachedFullList = all;
			GroupRegistry.setJeiAllItems(all.stream().flatMap(i -> i.getItemStack().stream()).toList());
			if (fluidType != null) {
				GroupRegistry.setJeiAllFluids(all.stream()
					.flatMap(i -> i.getIngredient(fluidType).stream())
					.map(o -> (Object) o)
					.toList());
			}
		} else if (all == null) {
			all = this.cg$getIngredientListUncached("").toList();
			this.cg$cachedFullList = all;
		}

		if (this.cg$pendingIndex != null && this.cg$pendingIndex.isDone()) {
			try { this.cg$ingredientGroupIndex = this.cg$pendingIndex.join(); }
			catch (Exception e) { this.cg$ingredientGroupIndex = null; }
			this.cg$pendingIndex = null;
		}
		if (this.cg$ingredientGroupIndex == null) {
			this.cg$ingredientGroupIndex = this.cg$buildIngredientGroupIndex(all);
		}

		this.cg$enabledGroupIds = GroupRegistry.getAllIncludingKubeJs().stream()
			.filter(GroupDefinition::enabled).map(GroupDefinition::id)
			.collect(Collectors.toSet());

		// Pass 1: bucket items and fluids
		Map<GroupDefinition, List<ITypedIngredient<ItemStack>>> itemGroups = new LinkedHashMap<>();
		Map<GroupDefinition, List<ITypedIngredient<FluidStack>>> fluidGroups = new LinkedHashMap<>();
		Map<GroupDefinition, List<ITypedIngredient<?>>> genericGroups = new LinkedHashMap<>();
		for (ITypedIngredient<?> ingredient : ingredients) {
			GroupDefinition group = this.cg$ingredientGroupIndex.get(ingredient);
			if (group == null || !this.cg$enabledGroupIds.contains(group.id())) continue;
			if (ingredient.getItemStack().isPresent()) {
				@SuppressWarnings("unchecked")
				ITypedIngredient<ItemStack> item = (ITypedIngredient<ItemStack>) ingredient;
				itemGroups.computeIfAbsent(group, x -> new ArrayList<>()).add(item);
			} else if (fluidType != null && ingredient.getIngredient(fluidType).isPresent()) {
				@SuppressWarnings("unchecked")
				ITypedIngredient<FluidStack> fluid = (ITypedIngredient<FluidStack>) ingredient;
				fluidGroups.computeIfAbsent(group, x -> new ArrayList<>()).add(fluid);
			} else {
				genericGroups.computeIfAbsent(group, x -> new ArrayList<>()).add(ingredient);
			}
		}

		// Pass 2: build structure cache
		List<IElement<?>> baseList = new ArrayList<>();
		List<String>      baseListGroupIds = new ArrayList<>();
		Map<String, List<IElement<?>>> childrenByGroupId = new HashMap<>();
		Set<String> emittedHeaders = new HashSet<>();
		boolean searchUngroupEnabled = Services.CONFIG.searchUngroupSmallGroups();
		int searchUngroupThreshold = Services.CONFIG.searchUngroupThreshold();

		for (ITypedIngredient<?> ingredient : ingredients) {
			GroupDefinition group = this.cg$ingredientGroupIndex.get(ingredient);
			if (group != null) {
				List<ITypedIngredient<ItemStack>> itemChildren = itemGroups.getOrDefault(group, List.of());
				List<ITypedIngredient<FluidStack>> fluidChildren = fluidGroups.getOrDefault(group, List.of());
				List<ITypedIngredient<?>> genericChildren = genericGroups.getOrDefault(group, List.of());
				int totalChildren = itemChildren.size() + fluidChildren.size() + genericChildren.size();
				if (totalChildren >= 2) {
					boolean ungroupForSearch = SearchUngroupPolicy.shouldUngroup(
						searchUngroupEnabled, this.cg$searchActiveForCache, totalChildren, searchUngroupThreshold);
					if (ungroupForSearch) {
						baseList.add(new IngredientElement<>(ingredient));
						baseListGroupIds.add(null);
						continue;
					}
					if (emittedHeaders.add(group.id())) {
						List<ITypedIngredient<?>> displayIngredients = group.iconIds().isEmpty()
							? List.of() : cg$resolveIconIds(group.iconIds());
						if (displayIngredients.isEmpty()) {
							List<ITypedIngredient<?>> allChildren = new ArrayList<>(totalChildren);
							allChildren.addAll(itemChildren);
							allChildren.addAll(fluidChildren);
							allChildren.addAll(genericChildren);
							displayIngredients = allChildren.subList(0, Math.min(2, allChildren.size()));
						}
						GroupIcon icon = new GroupIcon(group.id(), group.displayName().key(), group.displayName().fallback(), displayIngredients);
						ITypedIngredient<GroupIcon> typedIcon = TypedIngredient.createUnvalidated(GroupIcon.TYPE, icon);
						List<GroupPreviewEntry> previewEntries = new ArrayList<>(totalChildren);
						for (ITypedIngredient<ItemStack> child : itemChildren)
							previewEntries.add(GroupPreviewEntry.ofItem(child.getIngredient()));
						for (ITypedIngredient<FluidStack> child : fluidChildren)
							previewEntries.add(GroupPreviewEntry.ofFluid(child.getIngredient()));
						previewEntries.addAll(GroupPreviewEntry.fromTypedIngredients(genericChildren));
						Component countLabel = cg$buildCountLabel(itemChildren.size(), fluidChildren.size(), genericChildren.size());
						baseList.add(new GroupHeaderElement(typedIcon, countLabel, previewEntries, this::cg$toggleAndRebuildDisplay));
						baseListGroupIds.add(group.id());
						List<IElement<?>> children = new ArrayList<>(totalChildren);
						for (ITypedIngredient<ItemStack> child : itemChildren)
							children.add(new GroupChildElement(child, group.id()));
						for (ITypedIngredient<FluidStack> child : fluidChildren)
							children.add(new FluidChildElement(child, group.id()));
						for (ITypedIngredient<?> child : genericChildren)
							children.add(cg$wrapGenericChild(child, group.id()));
						childrenByGroupId.put(group.id(), children);
					}
					continue;
				}
			}
			baseList.add(new IngredientElement<>(ingredient));
			baseListGroupIds.add(null);
		}

		this.cg$baseList          = baseList;
		this.cg$baseListGroupIds  = baseListGroupIds;
		this.cg$childrenByGroupId = childrenByGroupId;
	}

	@Unique
	private static List<ITypedIngredient<?>> cg$resolveIconIds(List<String> iconIds) {
		List<ITypedIngredient<?>> result = new ArrayList<>(iconIds.size());
		for (String iconId : iconIds) {
			ResourceLocation loc = ResourceLocation.tryParse(iconId);
			if (loc == null) continue;
			Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(loc);
			if (item == net.minecraft.world.item.Items.AIR) continue;
			ItemStack stack = new ItemStack(item);
			result.add(TypedIngredient.createUnvalidated(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack));
		}
		return result;
	}

	@Unique
	private static Component cg$buildCountLabel(int itemCount, int fluidCount, int genericCount) {
		MutableComponent result = null;
		if (itemCount > 0) {
			result = Component.translatable(ModTranslationKeys.COUNT_ITEMS, itemCount);
		}
		if (fluidCount > 0) {
			MutableComponent part = Component.translatable(ModTranslationKeys.COUNT_FLUIDS, fluidCount);
			result = result == null ? part : result.append(", ").append(part);
		}
		if (genericCount > 0) {
			MutableComponent part = Component.translatable(ModTranslationKeys.COUNT_ENTRIES, genericCount);
			result = result == null ? part : result.append(", ").append(part);
		}
		return (result != null ? result : Component.empty()).withStyle(ChatFormatting.GRAY);
	}

	@Unique
	@SuppressWarnings("unchecked")
	private <T> IElement<?> cg$wrapGenericChild(ITypedIngredient<?> ingredient, String groupId) {
		return new GenericChildElement<>((ITypedIngredient<T>) ingredient, groupId);
	}

	/** Level-3: display list from cache. */
	@Unique
	private List<IElement<?>> cg$buildDisplayFromCache() {
		List<IElement<?>> result = new ArrayList<>(this.cg$baseList.size());
		for (int i = 0; i < this.cg$baseList.size(); i++) {
			result.add(this.cg$baseList.get(i));
			String groupId = this.cg$baseListGroupIds.get(i);
			if (groupId != null && GroupRegistry.isExpandedById(groupId)) {
				result.addAll(this.cg$childrenByGroupId.get(groupId));
			}
		}
		return result;
	}
}
