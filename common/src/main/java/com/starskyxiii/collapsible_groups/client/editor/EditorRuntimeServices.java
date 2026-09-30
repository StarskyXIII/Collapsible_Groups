package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import java.util.Optional;

/** Resolves the installed recipe-viewer implementation for editor operations. */
public final class EditorRuntimeServices {
	private EditorRuntimeServices() {}

	public static EditorRuntimeAccess get() {
		return require(find());
	}

	public static Optional<EditorRuntimeAccess> find() {
		return find(ViewerLifecycleCoordinator.global());
	}

	static Optional<EditorRuntimeAccess> find(ViewerLifecycleCoordinator coordinator) {
		return coordinator.activeAdapter()
			.map(adapter -> adapter.editorRuntimeAccess());
	}

	static EditorGroupAccess groups() { return groups(ViewerLifecycleCoordinator.global()); }

	static EditorGroupAccess groups(ViewerLifecycleCoordinator coordinator) {
		return require(findGroups(coordinator));
	}

	static Optional<EditorGroupAccess> findGroups() {
		return findGroups(ViewerLifecycleCoordinator.global());
	}

	static Optional<EditorGroupAccess> findGroups(ViewerLifecycleCoordinator coordinator) {
		return find(coordinator).map(EditorGroupAccess.class::cast);
	}

	static EditorIngredientAccess ingredients() { return ingredients(ViewerLifecycleCoordinator.global()); }

	static EditorIngredientAccess ingredients(ViewerLifecycleCoordinator coordinator) {
		return require(findIngredients(coordinator));
	}

	static Optional<EditorIngredientAccess> findIngredients() {
		return findIngredients(ViewerLifecycleCoordinator.global());
	}

	static Optional<EditorIngredientAccess> findIngredients(ViewerLifecycleCoordinator coordinator) {
		return find(coordinator).map(EditorIngredientAccess.class::cast);
	}

	static EditorPresentationAccess presentation() { return presentation(ViewerLifecycleCoordinator.global()); }

	static EditorPresentationAccess presentation(ViewerLifecycleCoordinator coordinator) {
		return require(findPresentation(coordinator));
	}

	static Optional<EditorPresentationAccess> findPresentation() {
		return findPresentation(ViewerLifecycleCoordinator.global());
	}

	static Optional<EditorPresentationAccess> findPresentation(ViewerLifecycleCoordinator coordinator) {
		return find(coordinator).map(EditorPresentationAccess.class::cast);
	}

	private static <T> T require(Optional<T> access) {
		return access.orElseThrow(() -> new IllegalStateException("No active recipe-viewer editor runtime"));
	}

}
