package com.starskyxiii.collapsible_groups.compat.jei.ui;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Double-buffered background renderer for group headers and expanded children.
 *
 * <p>Page changes are intentionally not detected through JEI's version-volatile
 * grid internals. A page flip can therefore show the previous page's tint for
 * one frame, matching the accepted one-frame background gap after expansion.
 */
public final class GroupBackgroundRenderer {
	private static final List<BackgroundPosition> currentFrame = new ArrayList<>();
	private static List<BackgroundPosition> previousFrame = List.of();

	private GroupBackgroundRenderer() {}

	public static void registerHeader(String groupId, int x, int y) {
		currentFrame.add(new BackgroundPosition(Kind.HEADER, groupId, x, y));
	}

	public static void registerChild(String groupId, int x, int y) {
		currentFrame.add(new BackgroundPosition(Kind.CHILD, groupId, x, y));
	}

	public static void renderPreviousFrame(GuiGraphics guiGraphics, boolean backgroundsVisible) {
		for (BackgroundPosition position : previousFrameForRender(backgroundsVisible)) {
			int color = switch (position.kind()) {
				case HEADER -> GroupThemeResolver.headerBackgroundColor(
					position.groupId(), GroupRegistry.isExpandedById(position.groupId()));
				case CHILD -> GroupThemeResolver.expandedGroupBackgroundColor(position.groupId());
			};
			guiGraphics.fill(position.x() - 1, position.y() - 1, position.x() + 17, position.y() + 17, color);
		}
	}

	/** Makes positions registered by overlays this frame available to the next frame. */
	public static void advanceFrame() {
		previousFrame = List.copyOf(currentFrame);
		currentFrame.clear();
	}

	/** Clears both frame buffers after any layout or projection discontinuity. */
	public static void clear() {
		currentFrame.clear();
		previousFrame = List.of();
	}

	static List<BackgroundPosition> previousFrameForRender(boolean backgroundsVisible) {
		if (!backgroundsVisible) {
			previousFrame = List.of();
			return List.of();
		}
		return previousFrame;
	}

	static int currentFrameSize() {
		return currentFrame.size();
	}

	public enum Kind {
		HEADER,
		CHILD
	}

	public record BackgroundPosition(Kind kind, String groupId, int x, int y) {}
}
