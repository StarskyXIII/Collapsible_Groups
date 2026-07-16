package com.starskyxiii.collapsible_groups.client.editor;

import java.util.ServiceLoader;

/** Resolves the installed recipe-viewer implementation for editor operations. */
public final class EditorRuntimeServices {
	private static final EditorRuntimeAccess ACCESS = ServiceLoader.load(EditorRuntimeAccess.class)
		.findFirst()
		.orElseThrow(() -> new IllegalStateException("No editor runtime access provider found"));

	private EditorRuntimeServices() {}

	public static EditorRuntimeAccess get() {
		return ACCESS;
	}
}
