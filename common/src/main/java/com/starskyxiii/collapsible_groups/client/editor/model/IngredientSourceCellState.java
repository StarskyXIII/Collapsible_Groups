package com.starskyxiii.collapsible_groups.client.editor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure-logic resolution of a source-grid cell's three-state visual
 * (normal / selected-in-current-group / overlap) from the selection state and
 * the ownership query result. Shared across loaders; no rendering here.
 *
 * <p>Precedence: explicit selection in the current group &gt; rule-covered by the
 * current group &gt; overlap (JEI winner is another group) &gt; normal. Overlap
 * cells remain clickable.
 *
 * <p>rule-covered cells (a full match of the current group's
 * own rules that was not explicitly picked) share the selected green visual but
 * are not toggleable — {@link #canToggleCurrentGroup()} is {@code false} so a click
 * is a no-op; the rule owns the membership.
 */
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

	/**
	 * Not explicitly picked, but a full match of the current group's own rules.
	 * Rendered like a selected cell (green) but not toggleable: the rule owns the
	 * membership, so a click is a no-op and users must edit the rules to change it.
	 * Any other-group ownership names are still carried for the tooltip.
	 */
	public static IngredientSourceCellState ruleCovered(List<String> ownerGroupNames) {
		return new IngredientSourceCellState(
			IngredientSourceCellVisualState.RULE_COVERED,
			false,
			ownerGroupNames,
			false
		);
	}

	/**
	 * Unselected here, but the JEI winner is another group. Still clickable —
	 * clicking adds the ingredient to the current group.
	 */
	public static IngredientSourceCellState overlap(List<String> ownerGroupNames) {
		return new IngredientSourceCellState(
			IngredientSourceCellVisualState.OVERLAP,
			false,
			ownerGroupNames,
			true
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
		return resolve(activeInCurrentGroup, false, ownerGroupNames, disabled);
	}

	/**
	 * Full precedence resolution: explicit selected &gt; rule-covered &gt; overlap
	 * &gt; normal (disabled short-circuits everything).
	 *
	 * @param activeInCurrentGroup explicitly picked in the current group
	 * @param ruleCoveredInCurrentGroup a full match of the current group's rules
	 *        (only honoured when not explicitly picked)
	 * @param ownerGroupNames the JEI winner group name(s) from other groups (may be empty)
	 */
	public static IngredientSourceCellState resolve(
		boolean activeInCurrentGroup,
		boolean ruleCoveredInCurrentGroup,
		List<String> ownerGroupNames,
		boolean disabled
	) {
		if (disabled) {
			return disabled();
		}
		if (activeInCurrentGroup) {
			return active(ownerGroupNames);
		}
		if (ruleCoveredInCurrentGroup) {
			return ruleCovered(ownerGroupNames);
		}
		if (ownerGroupNames != null && !ownerGroupNames.isEmpty()) {
			return overlap(ownerGroupNames);
		}
		return normal();
	}

	public boolean pressed() {
		return visualState == IngredientSourceCellVisualState.PRESSED_ACTIVE;
	}

	public boolean ruleCovered() {
		return visualState == IngredientSourceCellVisualState.RULE_COVERED;
	}

	/**
	 * Whether the cell should draw with the selected-green visual (fill + faint
	 * frame + corner tab): true for both an explicit selection and a rule-covered
	 * match. The two states diverge only in toggleability and tooltip text.
	 */
	public boolean renderedAsSelected() {
		return visualState == IngredientSourceCellVisualState.PRESSED_ACTIVE
			|| visualState == IngredientSourceCellVisualState.RULE_COVERED;
	}

	public boolean overlapped() {
		return visualState == IngredientSourceCellVisualState.OVERLAP;
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
