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
	private static boolean showBuiltin = true;
	private static boolean showKubeJs = true;
	private static boolean hideUsed = false;
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

	private static synchronized void ensureLoaded() {
		if (loaded) {
			return;
		}
		GroupConfig.UiState state = GroupConfig.loadUiState();
		showBuiltin = state.showBuiltin();
		showKubeJs = state.showKubeJs();
		hideUsed = state.hideUsed();
		loaded = true;
	}

	private static void persist() {
		boolean builtinSnapshot = showBuiltin;
		boolean kubeJsSnapshot = showKubeJs;
		boolean hideUsedSnapshot = hideUsed;
		PERSIST_EXECUTOR.submit(() -> GroupConfig.saveUiState(builtinSnapshot, kubeJsSnapshot, hideUsedSnapshot));
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
