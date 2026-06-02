package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupThemeResolver;
import com.starskyxiii.collapsible_groups.platform.Services;
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
		if (!Services.CONFIG.showGroupBackgrounds()) return;

		boolean expanded = GroupRegistry.isExpandedById(groupId);
		int background = GroupThemeResolver.headerBackgroundColor(groupId, expanded);

		// Semi-transparent background tint
		guiGraphics.fill(xOffset - 1, yOffset - 1, xOffset + 17, yOffset + 17,
			background);
	}
}
