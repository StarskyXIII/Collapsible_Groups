package com.starskyxiii.collapsible_groups.client.editor;

import java.util.List;

public record EditorIngredientTags(Object token, String type, Status status, Coverage coverage,
	boolean partial, List<String> tags) {
	public enum Status { READY, PENDING, UNAVAILABLE, TYPE_MISSING }
	public enum Coverage { REGISTRY_BACKED, OBSERVED_ONLY }
	public EditorIngredientTags { tags = List.copyOf(tags); }
	public static final EditorIngredientTags UNAVAILABLE = new EditorIngredientTags(new Object(), "",
		Status.UNAVAILABLE, Coverage.OBSERVED_ONLY, false, List.of());
}
