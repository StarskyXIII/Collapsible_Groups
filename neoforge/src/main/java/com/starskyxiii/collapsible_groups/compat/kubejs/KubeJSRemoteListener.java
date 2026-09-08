package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.ScriptedGroupStore;
import dev.latvian.mods.kubejs.recipe.viewer.server.FluidData;
import dev.latvian.mods.kubejs.recipe.viewer.server.ItemData;
import dev.latvian.mods.kubejs.recipe.viewer.server.RecipeViewerData;
import dev.latvian.mods.kubejs.recipe.viewer.server.RemoteRecipeViewerDataUpdatedEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NeoForge game-event-bus listener for KubeJS server-side recipe viewer data.
 *
 * When the server sends remote group definitions, this class stores them so
 * KubeJSGroupBridge can incorporate them the next time JEI rebuilds its
 * ingredient list.
 *
 * Register via {@code NeoForge.EVENT_BUS.register(KubeJSRemoteListener.class)}
 * when KubeJS is present (see CollapsibleGroups).
 *
 * This class directly references KubeJS types, so it must only be loaded
 * when KubeJS is present ??guarded by a ModList check at the call site.
 */
public final class KubeJSRemoteListener {

	private static final AtomicLong arrivals = new AtomicLong();
	private static final AtomicReference<RemoteSnapshot> pending =
		new AtomicReference<>(new RemoteSnapshot(0, List.of(), List.of()));

	private KubeJSRemoteListener() {}

	@SubscribeEvent
	public static void onRemoteData(RemoteRecipeViewerDataUpdatedEvent event) {
		long revision = arrivals.incrementAndGet();
		RecipeViewerData data = event.data;
		RemoteSnapshot next = data == null
			? new RemoteSnapshot(revision, List.of(), List.of())
			: new RemoteSnapshot(revision, List.copyOf(data.itemData().groupedEntries()),
				List.copyOf(data.fluidData().groupedEntries()));
		if (!installIfNewer(pending, next)) return;
		ScriptedGroupStore.invalidateAndNotify();
	}

	static boolean installIfNewer(AtomicReference<RemoteSnapshot> target, RemoteSnapshot next) {
		RemoteSnapshot current;
		do {
			current = target.get();
			if (current.revision() >= next.revision()) return false;
		} while (!target.compareAndSet(current, next));
		return true;
	}

	public static RemoteSnapshot snapshot() {
		return pending.get();
	}

	public static void clear() {
		long revision = arrivals.incrementAndGet();
		installIfNewer(pending, new RemoteSnapshot(revision, List.of(), List.of()));
		ScriptedGroupStore.invalidate();
	}

	public record RemoteSnapshot(long revision, List<ItemData.Group> itemGroups, List<FluidData.Group> fluidGroups) {
		public RemoteSnapshot {
			itemGroups = List.copyOf(itemGroups);
			fluidGroups = List.copyOf(fluidGroups);
		}
	}
}
