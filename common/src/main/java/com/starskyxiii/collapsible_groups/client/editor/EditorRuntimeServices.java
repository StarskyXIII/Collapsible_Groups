package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;

/** Resolves the installed recipe-viewer implementation for editor operations. */
public final class EditorRuntimeServices {
	private EditorRuntimeServices() {}

	public static EditorRuntimeAccess get() {
		return ViewerLifecycleCoordinator.global().activeAdapter()
			.map(adapter -> adapter.editorRuntimeAccess())
			.orElseThrow(() -> new IllegalStateException("No active recipe-viewer editor runtime"));
	}
}
