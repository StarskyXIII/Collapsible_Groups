package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.viewer.ViewerProjection;

import java.util.List;

/** Viewer-free classification of neutral display entries before EMI wrapper construction. */
public final class EmiProjectionTranslation {
	private EmiProjectionTranslation() {}

	public enum Kind { HEADER, RAW_INGREDIENT, PROJECTED_CHILD }
	public record Entry<E>(Kind kind, ViewerProjection.DisplayEntry<E> displayEntry) {}

	public static <E> List<Entry<E>> classify(ViewerProjection<E> projection) {
		return projection.displayEntries().stream().map(entry -> {
			Kind kind = entry instanceof ViewerProjection.DisplayHeader<?> ? Kind.HEADER
				: ((ViewerProjection.DisplayIngredient<?>) entry).parentGroupId().isPresent()
					? Kind.PROJECTED_CHILD : Kind.RAW_INGREDIENT;
			return new Entry<E>(kind, entry);
		}).toList();
	}
}
