package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Overlay drawn on group header elements in the JEI ingredient grid.
 * Only draws a semi-transparent background tint; stacked-icon rendering
 * is handled by {@link GroupIconRenderer}.
 */
public final class GroupExpandOverlay implements IDrawable {
	static final int EXPANDED_BACKGROUND = 0x18FFFFFF;
	static final int COLLAPSED_BACKGROUND = 0x26FFFFFF;
	private static final int EXPANDED_EDGE = 0x66FFFFFF;
	private static final int COLLAPSED_EDGE = 0x99FFFFFF;
	private final String groupId;

	public GroupExpandOverlay(String groupId) {
		this.groupId = groupId;
	}

	@Override
	public int getWidth() { return 16; }

	@Override
	public int getHeight() { return 16; }

	@Override
	public void draw(GuiGraphicsExtractor guiGraphics, int xOffset, int yOffset) {
		if (!GroupRegistry.isExpandedById(groupId)) {
			return;
		}
	}
}
