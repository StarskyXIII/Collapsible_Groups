package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import java.util.List;

public final class EditorPreviewCache {
	private EditorRuntimeAccess runtime;
	private Object generation;
	private List<EditorRuntimeAccess.PreviewEntry> entries;
	private List<GroupIconDefinition> definitions;
	private List<EditorRuntimeAccess.PreviewEntry> icons = List.of();

	public List<EditorRuntimeAccess.PreviewEntry> icons(EditorRuntimeAccess current,
		List<GroupIconDefinition> configured, List<EditorRuntimeAccess.PreviewEntry> fallback) {
		Object token = current.previewGeneration();
		if (runtime != current || generation != token || entries != fallback || !configured.equals(definitions)) {
			runtime = current;
			generation = token;
			entries = fallback;
			definitions = List.copyOf(configured);
			icons = token == null ? List.of() : current.resolveHeaderIcons(configured, fallback);
		}
		return icons;
	}

	public void clear() {
		runtime = null;
		generation = null;
		entries = null;
		definitions = null;
		icons = List.of();
	}
}
