package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Overlay drawn on group header elements in the JEI ingredient grid.
 *
 * <p>With the switch to {@link GroupIcon} as a custom ingredient type,
 * the stacked-icon rendering is now handled entirely by
 * {@link GroupIconRenderer}. This overlay only draws a semi-transparent
 * background tint to distinguish the group header from regular slots.
 */
public final class GroupExpandOverlay implements IDrawable {
	private final String groupId;

	public GroupExpandOverlay(String groupId) {
		this.groupId = groupId;
	}

	@Override
	public int getWidth() {
		return 16;
	}

	@Override
	public int getHeight() {
		return 16;
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
		boolean expanded = GroupRegistry.isExpandedById(groupId);

		// Semi-transparent background tint
		guiGraphics.fill(xOffset - 1, yOffset - 1, xOffset + 17, yOffset + 17,
			expanded ? 0x34FFFFFF : 0x53FFFFFF);
	}
}
