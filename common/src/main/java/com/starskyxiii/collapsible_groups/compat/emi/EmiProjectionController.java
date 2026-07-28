package com.starskyxiii.collapsible_groups.compat.emi;

import dev.emi.emi.api.stack.EmiIngredient;

import java.util.List;

/** Small last-mile cache used because ScreenSpace asks for the same list during layout, render and input. */
public final class EmiProjectionController {
	private static Object originalIdentity;
	private static int originalSize;
	private static int fingerprint;
	private static boolean search;
	private static String searchText = "";
	private static EmiViewerAdapter.ProjectionCacheKey readinessKey;
	private static List<? extends EmiIngredient> projected;

	private EmiProjectionController() {}

	public static synchronized List<? extends EmiIngredient> project(
		List<? extends EmiIngredient> original, boolean searchPanel
	) {
		String text = searchPanel ? dev.emi.emi.api.EmiApi.getSearchText() : "";
		EmiViewerAdapter.ProjectionCacheKey currentReadiness = EmiViewerAdapter.instance().projectionCacheKey();
		int currentFingerprint = fingerprint(original);
		if (originalIdentity == original && originalSize == original.size()
			&& fingerprint == currentFingerprint && search == searchPanel
			&& searchText.equals(text) && currentReadiness.equals(readinessKey)
			&& projected != null) return projected;
		List<? extends EmiIngredient> result = EmiViewerAdapter.instance().projectIndex(original, searchPanel);
		originalIdentity = original;
		originalSize = original.size();
		fingerprint = currentFingerprint;
		search = searchPanel;
		searchText = text;
		readinessKey = currentReadiness;
		// A raw fallback while bootstrap/index is unavailable is transient and must never be memoized.
		projected = shouldMemoize(currentReadiness) ? result : null;
		return result;
	}

	static boolean shouldMemoize(EmiViewerAdapter.ProjectionCacheKey key) {
		return key.bootstrapReady() && key.indexReady();
	}

	public static synchronized void clearCache() {
		originalIdentity = null;
		readinessKey = null;
		projected = null;
	}

	private static int fingerprint(List<? extends EmiIngredient> list) {
		int hash = list.size();
		if (!list.isEmpty()) {
			hash = 31 * hash + System.identityHashCode(list.get(0));
			hash = 31 * hash + System.identityHashCode(list.get(list.size() - 1));
		}
		return hash;
	}
}
