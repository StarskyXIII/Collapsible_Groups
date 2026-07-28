package com.starskyxiii.collapsible_groups.compat.jei.ui;

import com.starskyxiii.collapsible_groups.client.preview.ConnectedSlotBorderRenderer;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	public static void renderAndClear(GuiGraphics guiGraphics) {
		if (framePositions.isEmpty()) return;
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(0, 0, 200);
		try {
			for (Map.Entry<String, List<int[]>> entry : framePositions.entrySet()) {
				int color = GroupThemeResolver.expandedGroupBorderColor(entry.getKey());
				List<int[]> positions = entry.getValue();
				drawBorder(guiGraphics, positions, color);
			}
		} finally {
			guiGraphics.pose().popPose();
			framePositions.clear();
		}
	}

	// -----------------------------------------------------------------------
	// Border drawing
	// -----------------------------------------------------------------------

	/**
	 * Draws the cell-connected border for a set of 16x16 slot positions using an
	 * 18px slot pitch. Public so previews (e.g. the settings-mode group sample)
	 * render byte-identical borders to the live JEI panel.
	 */
	public static void drawBorder(GuiGraphics g, List<int[]> positions, int color) {
		ConnectedSlotBorderRenderer.drawBorder(g, positions, color);
	}
}
