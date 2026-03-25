package com.starskyxiii.collapsible_groups.persistence;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tracks which groups are currently expanded in JEI.
 * State is persisted to {@code config/collapsiblegroups/expand_state.json}
 * via {@link GroupConfig} whenever it changes.
 *
 * <p>Disk writes are submitted to a single background thread to avoid
 * blocking the Minecraft render thread on every expand/collapse click.
 */
public final class GroupExpandState {

	private static final ConcurrentHashMap<String, Boolean> EXPANDED = new ConcurrentHashMap<>();

	/** Daemon thread that serialises all expand-state saves off the render thread. */
	private static final ExecutorService PERSIST_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "CollapsibleGroups-Persist");
		t.setDaemon(true);
		return t;
	});

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(
			GroupExpandState::shutdownPersistExecutor,
			"CollapsibleGroups-PersistShutdown"
		));
	}

	private GroupExpandState() {}

	/** Populates the expand map from the persisted set of expanded IDs. */
	public static void load(Set<String> expandedIds) {
		EXPANDED.clear();
		expandedIds.forEach(id -> EXPANDED.put(id, true));
	}

	public static boolean isExpandedById(String id) {
		return EXPANDED.getOrDefault(id, false);
	}

	public static void toggleById(String id) {
		EXPANDED.compute(id, (k, v) -> v == null || !v);
		persist();
	}

	/** Removes the expand entry for a deleted group. */
	public static void remove(String id) {
		EXPANDED.remove(id);
	}

	private static void persist() {
		// Snapshot the current state synchronously, then write off the render thread.
		Set<String> snapshot = new HashSet<>();
		EXPANDED.forEach((id, isExpanded) -> { if (isExpanded) snapshot.add(id); });
		PERSIST_EXECUTOR.submit(() -> GroupConfig.saveExpandState(snapshot));
	}

	private static void shutdownPersistExecutor() {
		PERSIST_EXECUTOR.shutdown();
		try {
			if (!PERSIST_EXECUTOR.awaitTermination(3, TimeUnit.SECONDS)) {
				PERSIST_EXECUTOR.shutdownNow();
				PERSIST_EXECUTOR.awaitTermination(3, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			PERSIST_EXECUTOR.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
