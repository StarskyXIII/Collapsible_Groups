package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.element.GroupChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupHeaderElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.IngredientFilterHelper;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.library.ingredients.TypedIngredient;
import mezz.jei.gui.filter.IFilterTextSource;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
			String filterText = this.filterTextSource.getFilterText().toLowerCase(Locale.ROOT);
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

	/** Level-1: item-only index. Fluids/generic not supported on Fabric. */
	@Unique
	private Map<ITypedIngredient<?>, GroupDefinition> cg$buildIngredientGroupIndex(List<ITypedIngredient<?>> all) {
		return IngredientFilterHelper.buildItemGroupIndex(all);
	}

	/** Level-2: structure cache ??item groups only. */
	@Unique
	private void cg$buildStructureCache(List<ITypedIngredient<?>> ingredients) {
		List<ITypedIngredient<?>> all = this.cg$cachedFullList;
		if (GroupRegistry.isJeiAllItemsEmpty()) {
			if (all == null) all = this.cg$getIngredientListUncached("").toList();
			this.cg$cachedFullList = all;
			GroupRegistry.setJeiAllItems(all.stream().flatMap(i -> i.getItemStack().stream()).toList());
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

		// Pass 1: bucket items
		Map<GroupDefinition, List<ITypedIngredient<ItemStack>>> itemGroups = new LinkedHashMap<>();
		for (ITypedIngredient<?> ingredient : ingredients) {
			GroupDefinition group = this.cg$ingredientGroupIndex.get(ingredient);
			if (group == null || !this.cg$enabledGroupIds.contains(group.id())) continue;
			if (ingredient.getItemStack().isPresent()) {
				@SuppressWarnings("unchecked")
				ITypedIngredient<ItemStack> item = (ITypedIngredient<ItemStack>) ingredient;
				itemGroups.computeIfAbsent(group, x -> new ArrayList<>()).add(item);
			}
		}

		// Pass 2: build structure cache
		List<IElement<?>> baseList = new ArrayList<>();
		List<String>      baseListGroupIds = new ArrayList<>();
		Map<String, List<IElement<?>>> childrenByGroupId = new HashMap<>();
		Set<String> emittedHeaders = new HashSet<>();

		for (ITypedIngredient<?> ingredient : ingredients) {
			GroupDefinition group = this.cg$ingredientGroupIndex.get(ingredient);
			if (group != null) {
				List<ITypedIngredient<ItemStack>> itemChildren = itemGroups.getOrDefault(group, List.of());
				if (itemChildren.size() >= 2) {
					if (emittedHeaders.add(group.id())) {
						List<ITypedIngredient<?>> displayIngredients = group.iconIds().isEmpty()
							? List.of() : cg$resolveIconIds(group.iconIds());
						if (displayIngredients.isEmpty()) {
							displayIngredients = new ArrayList<>(
								itemChildren.subList(0, Math.min(2, itemChildren.size())));
						}
						GroupIcon icon = new GroupIcon(group.id(), group.displayName().key(), group.displayName().fallback(), displayIngredients);
						ITypedIngredient<GroupIcon> typedIcon = TypedIngredient.createUnvalidated(GroupIcon.TYPE, icon);
						List<GroupPreviewEntry> previewEntries = GroupPreviewEntry.fromTypedIngredients(
							(List<ITypedIngredient<?>>) (List<?>) itemChildren);
						Component countLabel = Component.translatable(ModTranslationKeys.COUNT_ITEMS, itemChildren.size()).withStyle(net.minecraft.ChatFormatting.GRAY);
						baseList.add(new GroupHeaderElement(typedIcon, countLabel, previewEntries, this::cg$toggleAndRebuildDisplay));
						baseListGroupIds.add(group.id());
						List<IElement<?>> children = new ArrayList<>(itemChildren.size());
						for (ITypedIngredient<ItemStack> child : itemChildren)
							children.add(new GroupChildElement(child, group.id()));
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
			Identifier loc = Identifier.tryParse(iconId);
			if (loc == null) continue;
			Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(loc).map(ref -> ref.value()).orElse(null);
			if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
			ItemStack stack = new ItemStack(item);
			result.add(TypedIngredient.createUnvalidated(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack));
		}
		return result;
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
