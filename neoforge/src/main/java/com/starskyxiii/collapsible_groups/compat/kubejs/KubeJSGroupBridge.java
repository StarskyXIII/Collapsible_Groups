package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsFilterComposition;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupIds;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupPublication;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweredGroup;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweringResult;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsMaterializationCapture;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.viewer.ViewerBootstrapContext;
import com.starskyxiii.collapsible_groups.viewer.ViewerBootstrapEntries;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;
import dev.latvian.mods.kubejs.plugin.builtin.event.RecipeViewerEvents;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import dev.latvian.mods.kubejs.recipe.viewer.server.FluidData;
import dev.latvian.mods.kubejs.recipe.viewer.server.ItemData;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class KubeJSGroupBridge {
	private static final String OWNER = "neoforge:kubejs7";
	private static final String CLIENT_ITEM = "client:item";
	private static final String CLIENT_FLUID = "client:fluid";
	private static final String REMOTE_ITEM = "remote:item";
	private static final String REMOTE_FLUID = "remote:fluid";

	private KubeJSGroupBridge() {}

	public static void applyGroupsNeutral(ViewerBootstrapContext<?> bootstrap) {
		applyGroups(bootstrap);
	}

	public static void applyGroups(ViewerBootstrapContext<?> bootstrap) {
		List<ItemStack> allItems = ViewerBootstrapEntries.itemStacks(bootstrap);
		List<FluidStack> allFluids = ViewerBootstrapEntries.resourceIds(bootstrap, ViewerIngredient.Kind.FLUID).stream()
			.map(BuiltInRegistries.FLUID::get)
			.filter(java.util.Objects::nonNull)
			.map(fluid -> new FluidStack(fluid, 1000))
			.toList();
		KubeJsGroupPublication.Session publication = KubeJsGroupPublication.begin(OWNER);

		if (CGEvents.GROUPS.hasListeners()) {
			CGGroupsKubeEvent event = new CGGroupsKubeEvent();
			if (postFailed(CGEvents.GROUPS, event,
				() -> CGEvents.GROUPS.post(ScriptType.CLIENT, event))) return;
			for (var entry : event.sources().entrySet()) {
				String source = entry.getKey();
				publication.replace(source, acceptedGroups(source, entry.getValue()));
			}
		}

		KubeJsMaterializationCapture itemCapture = publication.capture(CLIENT_ITEM);
		if (RecipeViewerEvents.GROUP_ENTRIES.hasListeners(RecipeViewerEntryType.ITEM)) {
			JEIGroupEntriesKubeEvent event = new JEIGroupEntriesKubeEvent(allItems, CLIENT_ITEM, itemCapture);
			if (postFailed(RecipeViewerEvents.GROUP_ENTRIES, event,
				() -> RecipeViewerEvents.GROUP_ENTRIES.post(
					ScriptType.CLIENT, RecipeViewerEntryType.ITEM, event))) return;
			publication.replace(CLIENT_ITEM, acceptedGroups(CLIENT_ITEM, event.collectedGroups()));
		}

		KubeJsMaterializationCapture fluidCapture = publication.capture(CLIENT_FLUID);
		if (RecipeViewerEvents.GROUP_ENTRIES.hasListeners(RecipeViewerEntryType.FLUID)) {
			JEIFluidGroupEntriesKubeEvent event = new JEIFluidGroupEntriesKubeEvent(allFluids, CLIENT_FLUID, fluidCapture);
			if (postFailed(RecipeViewerEvents.GROUP_ENTRIES, event,
				() -> RecipeViewerEvents.GROUP_ENTRIES.post(
					ScriptType.CLIENT, RecipeViewerEntryType.FLUID, event))) return;
			publication.replace(CLIENT_FLUID, acceptedGroups(CLIENT_FLUID, event.collectedGroups()));
		}

		for (String typeId : IngredientTypeIds.getAllIds().keySet()) {
			ViewerIngredientType<?> type = bootstrap.resolveType(typeId).orElse(null);
			if (type != null && !applyGenericType(typeId, type.ingredients(), publication)) return;
		}

		applyRemoteGroups(allItems, allFluids, publication);
		publication.publish();
	}

	private static boolean applyGenericType(String typeId, List<? extends ViewerIngredient<?>> ingredients,
		KubeJsGroupPublication.Session publication) {
		RecipeViewerEntryType entryType = RecipeViewerEntryType.fromString(typeId);
		if (entryType == null || !RecipeViewerEvents.GROUP_ENTRIES.hasListeners(entryType) || ingredients.isEmpty()) return true;
		String source = "client:generic:" + typeId;
		JEIGenericGroupEntriesKubeEvent<Object> event = new JEIGenericGroupEntriesKubeEvent<>(typeId, source);
		if (postFailed(RecipeViewerEvents.GROUP_ENTRIES, event,
			() -> RecipeViewerEvents.GROUP_ENTRIES.post(ScriptType.CLIENT, entryType, event))) return false;
		publication.replace(source, acceptedGroups(source, event.collectedGroups()));
		return true;
	}

	private static void applyRemoteGroups(List<ItemStack> allItems, List<FluidStack> allFluids,
		KubeJsGroupPublication.Session publication) {
		KubeJSRemoteListener.RemoteSnapshot remote = KubeJSRemoteListener.snapshot();
		KubeJsMaterializationCapture itemCapture = publication.capture(REMOTE_ITEM);
		List<KubeJsLoweredGroup> itemGroups = new ArrayList<>();
		for (ItemData.Group group : remote.itemGroups()) {
			String id = KubeJsGroupIds.remoteItem(group.groupId().toString());
			String name = group.description().getString();
			GroupFilter compiled = KubeJsFilterCompiler.compileItemFilter(group.filter());
			if (compiled != null && KubeJsFilterComposition.supportsTree(compiled)) {
				itemGroups.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.exact(compiled, REMOTE_ITEM)));
			} else if (KubeJsFilterCompiler.isMaterializableItemIdSet(group.filter())) {
				LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
				try {
					for (ItemStack stack : allItems) {
						if (group.filter().test(stack)) {
							nodes.add(Filters.itemId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
						}
					}
				} catch (RuntimeException exception) {
					itemGroups.add(unsupported(id, name, REMOTE_ITEM,
						"Remote item ID-set filter failed while testing the viewer generation: " + exception.getMessage()));
					continue;
				}
				itemGroups.add(materializedOrUnsupported(id, name, nodes, REMOTE_ITEM, itemCapture, "item"));
			} else {
				itemGroups.add(unsupported(id, name, REMOTE_ITEM,
					"Remote item filter uses partial components, counts, or an unknown ingredient; use item IDs, tags, or exact ItemStacks."));
			}
		}
		publication.replace(REMOTE_ITEM, acceptedGroups(REMOTE_ITEM, itemGroups));

		KubeJsMaterializationCapture fluidCapture = publication.capture(REMOTE_FLUID);
		List<KubeJsLoweredGroup> fluidGroups = new ArrayList<>();
		for (FluidData.Group group : remote.fluidGroups()) {
			String id = KubeJsGroupIds.remoteFluid(group.groupId().toString());
			String name = group.description().getString();
			GroupFilter compiled = KubeJsFilterCompiler.compileFluidFilter(group.filter());
			if (compiled != null && KubeJsFilterComposition.supportsTree(compiled)) {
				fluidGroups.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.exact(compiled, REMOTE_FLUID)));
			} else if (KubeJsFilterCompiler.isMaterializableFluidIdSet(group.filter())) {
				LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
				try {
					for (FluidStack stack : allFluids) {
						if (group.filter().test(stack)) {
							nodes.add(Filters.fluidId(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString()));
						}
					}
				} catch (RuntimeException exception) {
					fluidGroups.add(unsupported(id, name, REMOTE_FLUID,
						"Remote fluid ID-set filter failed while testing the viewer generation: " + exception.getMessage()));
					continue;
				}
				fluidGroups.add(materializedOrUnsupported(id, name, nodes, REMOTE_FLUID, fluidCapture, "fluid"));
			} else {
				fluidGroups.add(unsupported(id, name, REMOTE_FLUID,
					"Remote fluid filter uses amount/components or an unknown ingredient; use a fluid ID string or tag."));
			}
		}
		publication.replace(REMOTE_FLUID, acceptedGroups(REMOTE_FLUID, fluidGroups));
	}

	private static KubeJsLoweredGroup materializedOrUnsupported(String id, String name,
		LinkedHashSet<GroupFilter> nodes, String source, KubeJsMaterializationCapture capture, String kind) {
		GroupFilter lowered = KubeJsFilterComposition.any(new ArrayList<>(nodes));
		return lowered == null
			? unsupported(id, name, source, "The " + kind + " ID-set filter matched no IDs in the current viewer generation.")
			: new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.materialized(lowered, source, capture));
	}

	private static KubeJsLoweredGroup unsupported(String id, String name, String source, String reason) {
		return new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.unsupported(reason, source));
	}

	static List<KubeJsLoweredGroup> acceptedGroups(String source, List<KubeJsLoweredGroup> groups) {
		List<KubeJsLoweredGroup> accepted = new ArrayList<>(groups.size());
		for (KubeJsLoweredGroup group : groups) {
			if (group.lowering().kind() == KubeJsLoweringResult.Kind.UNSUPPORTED) {
				Constants.LOG.warn("[CollapsibleGroups] Rejecting KubeJS group '{}' from source '{}': {}",
					group.id(), source, group.lowering().reason());
			} else {
				accepted.add(group);
			}
		}
		return List.copyOf(accepted);
	}

	static boolean isPostError(EventResult result) {
		return result.type() == EventResult.Type.ERROR;
	}

	private static boolean postFailed(EventHandler handler, KubeEvent event, Supplier<EventResult> post) {
		synchronized (handler) {
			var previous = handler.exceptionHandler;
			AtomicBoolean failed = new AtomicBoolean();
			handler.exceptionHandler = (posted, container, error) -> {
				if (posted == event) failed.set(true);
				return previous == null ? error : previous.handle(posted, container, error);
			};
			try {
				return failedAfter(post.get(), failed);
			} finally {
				handler.exceptionHandler = previous;
			}
		}
	}

	private static boolean failedAfter(EventResult result, AtomicBoolean failed) {
		return failed.get() || isPostError(result);
	}
}
