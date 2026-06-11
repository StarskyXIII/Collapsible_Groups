package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.persistence.GroupConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Session-scoped UI preferences shared by the JEI manager and editor screens.
 *
 * <p>Preferences are lazy-loaded from disk on first access and persisted on
 * change so the screens reopen with the same toggles after restarting the game.
 */
public final class GroupUiState {
	/** Exclusive source view filter used by the Ore manager's segmented control. */
	public enum ManagerSourceFilter {
		ALL("all"),
		USER("user"),
		BUILTIN("builtin"),
		KUBEJS("kubejs");

		private final String id;

		ManagerSourceFilter(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public static ManagerSourceFilter fromId(String id) {
			for (ManagerSourceFilter filter : values()) {
				if (filter.id.equals(id)) return filter;
			}
			return ALL;
		}
	}

	private static boolean showBuiltin = true;
	private static boolean showKubeJs = true;
	private static boolean hideUsed = false;
	private static ManagerSourceFilter managerSourceFilter = ManagerSourceFilter.ALL;
	private static boolean loaded = false;

	private static final ExecutorService PERSIST_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "CollapsibleGroups-UiState");
		t.setDaemon(true);
		return t;
	});

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(
			GroupUiState::shutdownPersistExecutor,
			"CollapsibleGroups-UiStateShutdown"
		));
	}

	private GroupUiState() {}

	public static boolean showBuiltin() {
		ensureLoaded();
		return showBuiltin;
	}

	public static void setShowBuiltin(boolean value) {
		ensureLoaded();
		showBuiltin = value;
		persist();
	}

	public static boolean showKubeJs() {
		ensureLoaded();
		return showKubeJs;
	}

	public static void setShowKubeJs(boolean value) {
		ensureLoaded();
		showKubeJs = value;
		persist();
	}

	public static boolean hideUsed() {
		ensureLoaded();
		return hideUsed;
	}

	public static void setHideUsed(boolean value) {
		ensureLoaded();
		hideUsed = value;
		persist();
	}

	public static ManagerSourceFilter managerSourceFilter() {
		ensureLoaded();
		return managerSourceFilter;
	}

	public static void setManagerSourceFilter(ManagerSourceFilter value) {
		ensureLoaded();
		managerSourceFilter = value == null ? ManagerSourceFilter.ALL : value;
		persist();
	}

	private static synchronized void ensureLoaded() {
		if (loaded) {
			return;
		}
		GroupConfig.UiState state = GroupConfig.loadUiState();
		showBuiltin = state.showBuiltin();
		showKubeJs = state.showKubeJs();
		hideUsed = state.hideUsed();
		managerSourceFilter = ManagerSourceFilter.fromId(state.managerSourceFilter());
		loaded = true;
	}

	private static void persist() {
		boolean builtinSnapshot = showBuiltin;
		boolean kubeJsSnapshot = showKubeJs;
		boolean hideUsedSnapshot = hideUsed;
		String sourceFilterSnapshot = managerSourceFilter.id();
		PERSIST_EXECUTOR.submit(() ->
			GroupConfig.saveUiState(builtinSnapshot, kubeJsSnapshot, hideUsedSnapshot, sourceFilterSnapshot));
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
