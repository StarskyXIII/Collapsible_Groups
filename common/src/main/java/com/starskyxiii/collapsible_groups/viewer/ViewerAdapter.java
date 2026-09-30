package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;

/** Complete recipe-viewer integration surface. */
public interface ViewerAdapter<E, T> {
	String id();

	ViewerUniverseProvider<E> universeProvider();

	ViewerSearchState<E> searchState();

	ViewerBootstrapContext<E> bootstrapContext();

	ViewerPresentation<E, T> presentation();

	ViewerBookmarkPolicy<E> bookmarkPolicy();

	ViewerOverlayHook overlayHook();

	ViewerGroupIndex groupIndex();

	EditorRuntimeAccess editorRuntimeAccess();

	void onGroupChange(GroupChangeEvent.Kind kind);
}
