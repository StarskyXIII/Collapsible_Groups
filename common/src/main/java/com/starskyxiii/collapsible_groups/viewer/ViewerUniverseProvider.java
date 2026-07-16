package com.starskyxiii.collapsible_groups.viewer;

/** Supplies the viewer's complete ingredient universe. */
@FunctionalInterface
public interface ViewerUniverseProvider<E> {
	ViewerIngredientUniverse<E> universe();
}
