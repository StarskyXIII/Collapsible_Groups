package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record IngredientSourceCellState(
	IngredientSourceCellVisualState visualState,
	boolean activeInCurrentGroup,
	List<String> ownerGroupNames,
	boolean canToggleCurrentGroup
) {
	public IngredientSourceCellState {
		visualState = Objects.requireNonNull(visualState, "visualState");
		ownerGroupNames = copyNormalized(ownerGroupNames);
	}

	public static IngredientSourceCellState normal() {
		return new IngredientSourceCellState(
			IngredientSourceCellVisualState.NORMAL,
			false,
			List.of(),
			true
		);
	}

	public static IngredientSourceCellState active(List<String> ownerGroupNames) {
		return new IngredientSourceCellState(
			IngredientSourceCellVisualState.PRESSED_ACTIVE,
			true,
			ownerGroupNames,
			true
		);
	}

	public static IngredientSourceCellState muted(List<String> ownerGroupNames) {
		return new IngredientSourceCellState(
			IngredientSourceCellVisualState.PRESSED_MUTED,
			false,
			ownerGroupNames,
			false
		);
	}

	public static IngredientSourceCellState disabled() {
		return new IngredientSourceCellState(
			IngredientSourceCellVisualState.DISABLED,
			false,
			List.of(),
			false
		);
	}

	public static IngredientSourceCellState resolve(
		boolean activeInCurrentGroup,
		List<String> ownerGroupNames,
		boolean disabled
	) {
		if (disabled) {
			return disabled();
		}
		if (activeInCurrentGroup) {
			return active(ownerGroupNames);
		}
		if (ownerGroupNames != null && !ownerGroupNames.isEmpty()) {
			return muted(ownerGroupNames);
		}
		return normal();
	}

	public boolean pressed() {
		return visualState == IngredientSourceCellVisualState.PRESSED_ACTIVE
			|| visualState == IngredientSourceCellVisualState.PRESSED_MUTED;
	}

	public boolean muted() {
		return visualState == IngredientSourceCellVisualState.PRESSED_MUTED;
	}

	public boolean hasOwnerTooltip() {
		return !ownerGroupNames.isEmpty();
	}

	private static List<String> copyNormalized(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<String> out = new ArrayList<>(values.size());
		for (String value : values) {
			if (value == null) continue;
			String trimmed = value.trim();
			if (!trimmed.isEmpty()) {
				out.add(trimmed);
			}
		}
		return List.copyOf(out);
	}
}
