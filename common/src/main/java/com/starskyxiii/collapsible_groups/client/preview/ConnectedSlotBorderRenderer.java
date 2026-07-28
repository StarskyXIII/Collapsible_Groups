package com.starskyxiii.collapsible_groups.client.preview;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stateless connected-border topology shared by live viewers and editor previews. */
public final class ConnectedSlotBorderRenderer {
	public static final int SPACING = 18;

	public record Segment(int left, int top, int right, int bottom) {}

	private ConnectedSlotBorderRenderer() {}

	public static void drawBorder(GuiGraphics graphics, List<int[]> positions, int color) {
		for (Segment segment : segments(positions)) {
			graphics.fill(segment.left(), segment.top(), segment.right(), segment.bottom(), color);
		}
	}

	/** Returns the exact one-pixel rectangles used to draw a connected slot outline. */
	public static List<Segment> segments(List<int[]> positions) {
		if (positions.isEmpty()) return List.of();
		Set<Long> cellSet = new HashSet<>();
		for (int[] position : positions) cellSet.add(pack(position[0], position[1]));

		List<Segment> result = new ArrayList<>();
		for (int[] position : positions) {
			int cx = position[0], cy = position[1];
			int sl = cx - 1, st = cy - 1;
			boolean hasTop = cellSet.contains(pack(cx, cy - SPACING));
			boolean hasBottom = cellSet.contains(pack(cx, cy + SPACING));
			boolean hasLeft = cellSet.contains(pack(cx - SPACING, cy));
			boolean hasRight = cellSet.contains(pack(cx + SPACING, cy));
			boolean hasTopLeft = cellSet.contains(pack(cx - SPACING, cy - SPACING));
			boolean hasTopRight = cellSet.contains(pack(cx + SPACING, cy - SPACING));
			boolean hasBottomLeft = cellSet.contains(pack(cx - SPACING, cy + SPACING));
			boolean hasBottomRight = cellSet.contains(pack(cx + SPACING, cy + SPACING));

			if (!hasTop) {
				int start = hasLeft && hasTopLeft ? -1 : 0;
				int end = hasRight && hasTopRight ? -1 : 0;
				result.add(new Segment(sl + start, st, sl + 18 - end, st + 1));
			}
			if (!hasBottom) {
				int start = hasLeft && hasBottomLeft ? -1 : 0;
				int end = hasRight && hasBottomRight ? -1 : 0;
				result.add(new Segment(sl + start, st + 17, sl + 18 - end, st + 18));
			}
			if (!hasLeft) {
				int start = !hasTop && !hasTopLeft ? 1 : 0;
				int end = !hasBottom && !hasBottomLeft ? 1 : 0;
				result.add(new Segment(sl, st + start, sl + 1, st + 18 - end));
			}
			if (!hasRight) {
				int start = !hasTop && !hasTopRight ? 1 : 0;
				int end = !hasBottom && !hasBottomRight ? 1 : 0;
				result.add(new Segment(sl + 17, st + start, sl + 18, st + 18 - end));
			}
		}
		return List.copyOf(result);
	}

	private static long pack(int x, int y) {
		return ((long) (x + 100000)) << 32 | (y + 100000);
	}
}
