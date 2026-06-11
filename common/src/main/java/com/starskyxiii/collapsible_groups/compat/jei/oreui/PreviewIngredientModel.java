package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PreviewIngredientModel(
	PreviewIngredientKind kind,
	String id,
	String displayName,
	List<String> ownerGroupNames
) {
	public PreviewIngredientModel {
		kind = Objects.requireNonNull(kind, "kind");
		id = requireNonBlank(id, "id");
		displayName = normalize(displayName);
		ownerGroupNames = copyNormalized(ownerGroupNames);
	}

	public static PreviewIngredientModel item(String id) {
		return new PreviewIngredientModel(PreviewIngredientKind.ITEM, id, null, List.of());
	}

	public static PreviewIngredientModel fluid(String id) {
		return new PreviewIngredientModel(PreviewIngredientKind.FLUID, id, null, List.of());
	}

	public static PreviewIngredientModel generic(String id) {
		return new PreviewIngredientModel(PreviewIngredientKind.GENERIC, id, null, List.of());
	}

	public boolean hasOwnerGroups() {
		return !ownerGroupNames.isEmpty();
	}

	private static String requireNonBlank(String value, String name) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return normalized;
	}

	private static List<String> copyNormalized(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<String> out = new ArrayList<>(values.size());
		for (String value : values) {
			String normalized = normalize(value);
			if (normalized != null) {
				out.add(normalized);
			}
		}
		return List.copyOf(out);
	}

	private static String normalize(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
