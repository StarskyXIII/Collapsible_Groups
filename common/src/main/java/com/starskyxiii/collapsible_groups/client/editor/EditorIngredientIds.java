package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public record EditorIngredientIds(Object token, String type, Status status, boolean partial, List<String> ids) {
	public enum Status { READY, PENDING, UNAVAILABLE, TYPE_MISSING }
	public EditorIngredientIds { ids = List.copyOf(ids); }
	public static final EditorIngredientIds UNAVAILABLE = new EditorIngredientIds(new Object(), "",
		Status.UNAVAILABLE, false, List.of());
	public static final EditorIngredientIds PENDING = new EditorIngredientIds(new Object(), "",
		Status.PENDING, false, List.of());

	public static <E> ViewerIngredientType<E> findType(List<ViewerIngredientType<E>> types, String requested) {
		if (types == null) return null;
		return types.stream().filter(type -> type.matchesId(requested)
			&& !type.canonicalId().equals("item") && !type.canonicalId().equals("fluid"))
			.findFirst().orElse(null);
	}

	public static <E> Iterator<Supplier<String>> sources(ViewerIngredientType<E> type) {
		return type.ingredients().stream().<Supplier<String>>map(entry -> () -> {
			if (entry.kind() != ViewerIngredient.Kind.GENERIC || !entry.identity().typeId().equals(type.canonicalId())) return null;
			var id = entry.view().resourceLocation();
			return id == null ? null : id.toString();
		}).iterator();
	}
}
