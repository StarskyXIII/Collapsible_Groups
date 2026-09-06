package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import java.util.Optional;

/** Resolves the installed recipe-viewer implementation for editor operations. */
public final class EditorRuntimeServices {
	private EditorRuntimeServices() {}

	public static EditorRuntimeAccess get() {
		return find()
			.orElseThrow(() -> new IllegalStateException("No active recipe-viewer editor runtime"));
	}

	public static Optional<EditorRuntimeAccess> find() {
		return ViewerLifecycleCoordinator.global().activeAdapter()
			.map(adapter -> adapter.editorRuntimeAccess());
	}
}
