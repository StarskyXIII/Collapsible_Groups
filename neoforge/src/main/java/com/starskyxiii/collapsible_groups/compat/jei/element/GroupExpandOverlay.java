package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBackgroundRenderer;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Registers group-header positions for the next background pre-pass.
 * Stacked-icon rendering is handled by {@link GroupIconRenderer}.
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
		GroupBackgroundRenderer.registerHeader(groupId, xOffset, yOffset);
	}
}
