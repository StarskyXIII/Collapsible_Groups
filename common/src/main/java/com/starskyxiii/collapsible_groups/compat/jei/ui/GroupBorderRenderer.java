package com.starskyxiii.collapsible_groups.compat.jei.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stateless per-frame border renderer for expanded collapsible groups.
 *
 * Modelled after REI's {@code CollapsedEntriesBorderRenderer}: slot overlays
 * call {@link #registerPosition} as they are individually drawn, then
 * {@link #renderAndClear} is called once at the end of JEI's drawScreen pass
 * to draw all group borders in a single go and clear the accumulator.
 *
 * Because all positions are collected and drawn in the same render frame there
 * is no need for double-buffering, frame-detection timers, or any per-group
 * state object.
 */
public final class GroupBorderRenderer {
	private static final int SPACING = 18;
	private static final int COLOR   = 0x66FFFFFF;

	/** Positions registered this render pass, keyed by group id. */
	private static final Map<String, List<int[]>> framePositions = new LinkedHashMap<>();

	private GroupBorderRenderer() {}

	// -----------------------------------------------------------------------
	// Registration (called from slot overlays)
	// -----------------------------------------------------------------------

	/**
	 * Records a slot's screen position for the current render pass.
	 * Both the group header overlay and each child overlay call this.
	 */
	public static void registerPosition(String groupId, int x, int y) {
		framePositions.computeIfAbsent(groupId, k -> new ArrayList<>()).add(new int[]{x, y});
	}

	// -----------------------------------------------------------------------
	// Rendering (called from MixinIngredientListOverlay after all entries)
	// -----------------------------------------------------------------------

	/**
	 * Draws the connected border for every group that registered positions this
	 * frame, then clears the accumulator.  Called by
	 * {@code MixinIngredientListOverlay} at the tail of {@code drawScreen}.
	 */
	public static void renderAndClear(GuiGraphicsExtractor guiGraphics) {
		if (framePositions.isEmpty()) return;
		guiGraphics.pose().pushMatrix();
		guiGraphics.nextStratum();
		try {
			for (List<int[]> positions : framePositions.values()) {
				drawBorder(guiGraphics, positions);
			}
		} finally {
			guiGraphics.pose().popMatrix();
			framePositions.clear();
		}
	}

	// -----------------------------------------------------------------------
	// Border drawing
	// -----------------------------------------------------------------------

	private static void drawBorder(GuiGraphicsExtractor g, List<int[]> positions) {
		if (positions.isEmpty()) return;

		Set<Long> cellSet = new HashSet<>();
		for (int[] pos : positions) {
			cellSet.add(pack(pos[0], pos[1]));
		}

		for (int[] pos : positions) {
			int cx = pos[0], cy = pos[1];
			int sl = cx - 1, st = cy - 1;

			boolean hasTop         = cellSet.contains(pack(cx,           cy - SPACING));
			boolean hasBottom      = cellSet.contains(pack(cx,           cy + SPACING));
			boolean hasLeft        = cellSet.contains(pack(cx - SPACING, cy          ));
			boolean hasRight       = cellSet.contains(pack(cx + SPACING, cy          ));
			boolean hasTopLeft     = cellSet.contains(pack(cx - SPACING, cy - SPACING));
			boolean hasTopRight    = cellSet.contains(pack(cx + SPACING, cy - SPACING));
			boolean hasBottomLeft  = cellSet.contains(pack(cx - SPACING, cy + SPACING));
			boolean hasBottomRight = cellSet.contains(pack(cx + SPACING, cy + SPACING));

			// TOP: fStart/fEnd = -1 - extend 1px outward at diagonal corners
			if (!hasTop) {
				int fS = (hasLeft  && hasTopLeft)    ? -1 : 0;
				int fE = (hasRight && hasTopRight)   ? -1 : 0;
				g.fill(sl + fS, st,      sl + 18 - fE, st + 1,  COLOR);
			}

			// BOTTOM
			if (!hasBottom) {
				int fS = (hasLeft  && hasBottomLeft)  ? -1 : 0;
				int fE = (hasRight && hasBottomRight) ? -1 : 0;
				g.fill(sl + fS, st + 17, sl + 18 - fE, st + 18, COLOR);
			}

			// LEFT: fStart/fEnd = 1 - inset 1px at exposed outer corners
			if (!hasLeft) {
				int fS = (!hasTop    && !hasTopLeft)    ? 1 : 0;
				int fE = (!hasBottom && !hasBottomLeft) ? 1 : 0;
				g.fill(sl,      st + fS, sl + 1,  st + 18 - fE, COLOR);
			}

			// RIGHT
			if (!hasRight) {
				int fS = (!hasTop    && !hasTopRight)    ? 1 : 0;
				int fE = (!hasBottom && !hasBottomRight) ? 1 : 0;
				g.fill(sl + 17, st + fS, sl + 18, st + 18 - fE, COLOR);
			}
		}
	}

	private static long pack(int x, int y) {
		return ((long) (x + 100000)) << 32 | (y + 100000);
	}
}
