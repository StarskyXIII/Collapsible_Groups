package com.starskyxiii.collapsible_groups.compat.jei.oreui;

/**
 * Visual state of a source-grid cell in the editor's contents mode.
 *
 * <p>{@code PRESSED_MUTED} (red "blocked", not
 * clickable) was replaced by {@link #OVERLAP} — a neutral amber corner tab +
 * faint amber frame. Overlap cells stay clickable: clicking adds the ingredient
 * to the current group (after which the cell becomes {@link #PRESSED_ACTIVE}).
 * The JEI winner shown in the tooltip is decided by group priority.
 *
 * <p>{@link #RULE_COVERED} added — the ingredient is a full
 * match of the current group's own rules but was not explicitly picked. It shares
 * the selected green visual (fill + faint frame + corner tab) but is <em>not</em>
 * toggleable (clicking does nothing; the rule owns it), so users must go to the
 * rules mode to change it. Precedence: explicit selected &gt; rule-covered &gt;
 * overlap &gt; normal.
 */
public enum IngredientSourceCellVisualState {
	NORMAL,
	PRESSED_ACTIVE,
	RULE_COVERED,
	OVERLAP,
	DISABLED
}
