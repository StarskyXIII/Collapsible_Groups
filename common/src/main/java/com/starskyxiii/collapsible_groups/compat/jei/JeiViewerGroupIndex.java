package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.GroupProjectionEngine;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** JEI cache/index implementation of the neutral {@link ViewerGroupIndex} seam. */
public final class JeiViewerGroupIndex implements ViewerGroupIndex {
	private static final JeiViewerGroupIndex INSTANCE = new JeiViewerGroupIndex();
	private static final ExecutorService REBUILD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "CG-IndexRebuild");
		thread.setDaemon(true);
		return thread;
	});

	private volatile @Nullable GroupCandidateIndex candidateIndex;
	private volatile ViewerIngredientUniverse<ITypedIngredient<?>> universe =
		new ViewerIngredientUniverse<>(List.of());
	private volatile CompletableFuture<Void> readyFuture = CompletableFuture.completedFuture(null);
	private volatile @Nullable Supplier<GroupCandidateIndex> rebuildSource;
	private volatile Executor completionExecutor = Runnable::run;
	private volatile Runnable rebuildListener = () -> {};
	private volatile List<GroupDefinition> currentGroups = List.of();
	private long rebuildSequence;

	private volatile @Nullable Map<String, List<ItemStack>> resolvedItemsByGroup;
	private volatile @Nullable Map<String, List<Object>> resolvedFluidsByGroup;
	private volatile @Nullable Map<String, List<ItemStack>> fullMatchItemsByGroup;
	private volatile @Nullable Map<String, List<Object>> fullMatchFluidsByGroup;
	private volatile @Nullable Map<String, List<GenericIngredientRef>> fullMatchGenericByGroup;
	private volatile @Nullable Map<String, Set<String>> itemIdToGroupIds;
	private volatile @Nullable Map<String, Set<String>> fluidIdToGroupIds;
	private volatile boolean previewCachesValid;

	private JeiViewerGroupIndex() {}

	public static JeiViewerGroupIndex instance() {
		return INSTANCE;
	}

	public void configureRebuild(Supplier<GroupCandidateIndex> source, Executor completionExecutor,
		Runnable rebuildListener) {
		this.rebuildSource = source;
		this.completionExecutor = completionExecutor;
		this.rebuildListener = rebuildListener;
	}

	public void updateUniverse(ViewerIngredientUniverse<ITypedIngredient<?>> universe) {
		this.universe = universe;
	}

	public synchronized void reset() {
		rebuildSequence++;
		candidateIndex = null;
		universe = new ViewerIngredientUniverse<>(List.of());
		readyFuture = CompletableFuture.completedFuture(null);
		rebuildSource = null;
		completionExecutor = Runnable::run;
		rebuildListener = () -> {};
		currentGroups = List.of();
		clearResolvedCaches();
		clearPreviewCaches();
	}

	public synchronized void invalidateCandidates() {
		candidateIndex = null;
		clearResolvedCaches();
	}

	public void publishCandidateIndex(GroupCandidateIndex index, List<GroupDefinition> groups) {
		candidateIndex = index;
		refreshResolvedOwnership(index, groups);
	}

	@Override
	public Optional<GroupCandidateIndex> candidates() {
		return Optional.ofNullable(candidateIndex);
	}

	@Override
	public boolean ready() {
		return candidateIndex != null && resolvedItemsByGroup != null && resolvedFluidsByGroup != null;
	}

	@Override
	public CompletableFuture<Void> whenReady() {
		return readyFuture;
	}

	@Override
	public Optional<String> resolveOwner(ViewerIngredientIdentity identity, List<GroupDefinition> groups) {
		return Optional.ofNullable(resolveOwnership(groups).get(identity));
	}

	@Override
	public Map<ViewerIngredientIdentity, String> resolveOwnership(List<GroupDefinition> groups) {
		GroupCandidateIndex current = candidateIndex;
		return current == null ? Map.of() : GroupProjectionEngine.resolveOwnership(current, groups);
	}

	@Override
	public synchronized void onGroupChange(GroupChangeEvent.Kind kind, List<GroupDefinition> groups) {
		currentGroups = List.copyOf(groups);
		switch (kind) {
			case ENABLED -> {
				GroupCandidateIndex current = candidateIndex;
				if (current != null) refreshResolvedOwnership(current, groups);
			}
			case STRUCTURE -> { }
			case FULL, KUBEJS_REPLACE -> {
				candidateIndex = null;
				clearResolvedCaches();
				clearPreviewCaches();
				startConfiguredRebuild(groups);
			}
		}
	}

	private void startConfiguredRebuild(List<GroupDefinition> groups) {
		Supplier<GroupCandidateIndex> source = rebuildSource;
		if (source == null) {
			readyFuture = CompletableFuture.completedFuture(null);
			return;
		}
		long sequence = ++rebuildSequence;
		CompletableFuture<GroupCandidateIndex> build = CompletableFuture.supplyAsync(source, REBUILD_EXECUTOR);
		CompletableFuture<Void> published = build.thenAccept(index -> {
			synchronized (this) {
				if (sequence != rebuildSequence) return;
				publishCandidateIndex(index, currentGroups);
			}
		});
		readyFuture = published;
		published.thenRunAsync(() -> {
			synchronized (this) {
				if (sequence != rebuildSequence) return;
			}
			rebuildListener.run();
		}, completionExecutor);
	}

	/** Starts the configured rebuild for a cold editor without ever running it on the render thread. */
	public synchronized CompletableFuture<Void> ensureReadyAsync(List<GroupDefinition> groups) {
		currentGroups = List.copyOf(groups);
		if (ready()) return CompletableFuture.completedFuture(null);
		if (!readyFuture.isDone()) return readyFuture;
		startConfiguredRebuild(groups);
		return readyFuture;
	}

	public CompletableFuture<Void> prepareEditorAsync(List<GroupDefinition> groups, Runnable warmCaches) {
		return ensureReadyAsync(groups).thenRunAsync(warmCaches, REBUILD_EXECUTOR);
	}

	private void refreshResolvedOwnership(GroupCandidateIndex index, List<GroupDefinition> groups) {
		Map<ViewerIngredientIdentity, String> ownership = GroupProjectionEngine.resolveOwnership(index, groups);
		Map<String, List<ItemStack>> items = new LinkedHashMap<>();
		Map<String, List<Object>> fluids = new LinkedHashMap<>();
		Map<String, Set<String>> itemIds = new LinkedHashMap<>();
		Map<String, Set<String>> fluidIds = new LinkedHashMap<>();
		for (GroupDefinition group : groups) {
			items.put(group.id(), new ArrayList<>());
			fluids.put(group.id(), new ArrayList<>());
		}
		for (ViewerIngredient<ITypedIngredient<?>> ingredient : universe.ordered()) {
			String groupId = ownership.get(ingredient.identity());
			if (groupId == null) continue;
			switch (ingredient.kind()) {
				case ITEM -> ingredient.entry().getItemStack().ifPresent(stack -> {
					items.computeIfAbsent(groupId, ignored -> new ArrayList<>()).add(stack);
					String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
					itemIds.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(groupId);
				});
				case FLUID -> {
					Object fluid = ingredient.entry().getIngredient();
					fluids.computeIfAbsent(groupId, ignored -> new ArrayList<>()).add(fluid);
					String id = Services.PLATFORM.getFluidId(fluid);
					if (id != null) fluidIds.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(groupId);
				}
				case GENERIC -> { }
			}
		}
		setResolvedItemsByGroup(items);
		setResolvedFluidsByGroup(fluids);
		itemIdToGroupIds = freezeSetMap(itemIds);
		fluidIdToGroupIds = freezeSetMap(fluidIds);
	}

	public @Nullable List<ItemStack> resolvedItems(String groupId) {
		Map<String, List<ItemStack>> cache = resolvedItemsByGroup;
		return cache == null ? null : cache.get(groupId);
	}
	public @Nullable Map<String, List<ItemStack>> resolvedItemsCache() { return resolvedItemsByGroup; }
	public @Nullable Map<String, List<Object>> resolvedFluidsCache() { return resolvedFluidsByGroup; }

	public @Nullable List<Object> resolvedFluids(String groupId) {
		Map<String, List<Object>> cache = resolvedFluidsByGroup;
		return cache == null ? null : cache.get(groupId);
	}

	public @Nullable Map<String, List<ItemStack>> fullMatchItems() { return fullMatchItemsByGroup; }
	public @Nullable Map<String, List<Object>> fullMatchFluids() { return fullMatchFluidsByGroup; }
	public @Nullable Map<String, List<GenericIngredientRef>> fullMatchGeneric() { return fullMatchGenericByGroup; }
	public @Nullable Map<String, Set<String>> itemReverseIndex() { return itemIdToGroupIds; }
	public @Nullable Map<String, Set<String>> fluidReverseIndex() { return fluidIdToGroupIds; }
	public boolean previewCachesValid() { return previewCachesValid; }

	public void setResolvedItemsByGroup(Map<String, List<ItemStack>> values) {
		resolvedItemsByGroup = freezeListMap(values);
	}
	public void setResolvedFluidsByGroup(Map<String, List<Object>> values) {
		resolvedFluidsByGroup = freezeListMap(values);
	}
	public void setFullMatchItemsByGroup(Map<String, List<ItemStack>> values) {
		fullMatchItemsByGroup = freezeListMap(values);
		previewCachesValid = true;
	}
	public void setFullMatchFluidsByGroup(Map<String, List<Object>> values) {
		fullMatchFluidsByGroup = freezeListMap(values);
		previewCachesValid = true;
	}
	public void setFullMatchGenericByGroup(Map<String, List<GenericIngredientRef>> values) {
		fullMatchGenericByGroup = freezeListMap(values);
		previewCachesValid = true;
	}
	public void setItemReverseIndex(Map<String, Set<String>> values) { itemIdToGroupIds = freezeSetMap(values); }
	public void setFluidReverseIndex(Map<String, Set<String>> values) { fluidIdToGroupIds = freezeSetMap(values); }

	public void clearResolvedCaches() {
		resolvedItemsByGroup = null;
		resolvedFluidsByGroup = null;
		itemIdToGroupIds = null;
		fluidIdToGroupIds = null;
	}

	public void clearPreviewCaches() {
		fullMatchItemsByGroup = null;
		fullMatchFluidsByGroup = null;
		fullMatchGenericByGroup = null;
		previewCachesValid = false;
	}

	public void invalidateFirstMatch(String groupId) {
		Map<String, List<ItemStack>> items = resolvedItemsByGroup;
		if (items != null) items.remove(groupId);
		Map<String, List<Object>> fluids = resolvedFluidsByGroup;
		if (fluids != null) fluids.remove(groupId);
	}

	public void invalidateFullMatch(String groupId) {
		Map<String, List<ItemStack>> items = fullMatchItemsByGroup;
		if (items != null) items.remove(groupId);
		Map<String, List<Object>> fluids = fullMatchFluidsByGroup;
		if (fluids != null) fluids.remove(groupId);
		Map<String, List<GenericIngredientRef>> generic = fullMatchGenericByGroup;
		if (generic != null) generic.remove(groupId);
	}

	public Map<String, List<ItemStack>> ensureFullMatchItems() {
		Map<String, List<ItemStack>> cache = fullMatchItemsByGroup;
		if (cache != null) return cache;
		synchronized (this) {
			if (fullMatchItemsByGroup == null) fullMatchItemsByGroup = new ConcurrentHashMap<>();
			previewCachesValid = true;
			return fullMatchItemsByGroup;
		}
	}
	public Map<String, List<Object>> ensureFullMatchFluids() {
		Map<String, List<Object>> cache = fullMatchFluidsByGroup;
		if (cache != null) return cache;
		synchronized (this) {
			if (fullMatchFluidsByGroup == null) fullMatchFluidsByGroup = new ConcurrentHashMap<>();
			previewCachesValid = true;
			return fullMatchFluidsByGroup;
		}
	}
	public Map<String, List<GenericIngredientRef>> ensureFullMatchGeneric() {
		Map<String, List<GenericIngredientRef>> cache = fullMatchGenericByGroup;
		if (cache != null) return cache;
		synchronized (this) {
			if (fullMatchGenericByGroup == null) fullMatchGenericByGroup = new ConcurrentHashMap<>();
			previewCachesValid = true;
			return fullMatchGenericByGroup;
		}
	}

	private static <T> Map<String, List<T>> freezeListMap(Map<String, List<T>> values) {
		Map<String, List<T>> copy = new ConcurrentHashMap<>(Math.max(16, values.size() * 2));
		values.forEach((key, value) -> copy.put(key, List.copyOf(value)));
		return copy;
	}

	private static Map<String, Set<String>> freezeSetMap(Map<String, Set<String>> values) {
		Map<String, Set<String>> copy = new ConcurrentHashMap<>(Math.max(16, values.size() * 2));
		values.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
		return copy;
	}
}
