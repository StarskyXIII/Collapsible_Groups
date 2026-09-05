package com.starskyxiii.collapsible_groups.client.preview;

import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess.PreviewEntry;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class PreviewRenderCache {
	private final Map<List<PreviewEntry>, List<GroupPreviewEntry>> snapshots = new IdentityHashMap<>();
	private Object generation;

	public List<GroupPreviewEntry> resolve(Object token, List<PreviewEntry> entries,
		Function<PreviewEntry, GroupPreviewEntry> convert) {
		if (generation != token) {
			clear();
			generation = token;
		}
		if (token == null) return List.of();
		List<GroupPreviewEntry> cached = snapshots.get(entries);
		if (cached != null) return cached;
		if (snapshots.size() == 2) snapshots.clear();
		List<GroupPreviewEntry> result = entries.stream().map(convert).toList();
		snapshots.put(entries, result);
		return result;
	}

	public void clear() {
		generation = null;
		snapshots.clear();
	}
}
