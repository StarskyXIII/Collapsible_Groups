package com.starskyxiii.collapsible_groups.client.preview;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight renderable preview entry that can represent an item, fluid, or
 * arbitrary viewer ingredient for mixed group previews.
 */
public final class GroupPreviewEntry {
	private final ItemStack item;
	private final Renderer viewerRenderer;

	private GroupPreviewEntry(ItemStack item, Renderer viewerRenderer) {
		this.item = item;
		this.viewerRenderer = viewerRenderer;
	}

	public static GroupPreviewEntry ofItem(ItemStack stack) {
		return new GroupPreviewEntry(stack, null);
	}

	/** Adapter-neutral factory for viewer-specific preview renderers. */
	public static GroupPreviewEntry ofRenderer(PreviewRenderer renderer) {
		return new GroupPreviewEntry(null, renderer::render);
	}

	public void render(GuiGraphics guiGraphics, int x, int y) {
		if (item != null) {
			guiGraphics.renderItem(item, x, y);
			return;
		}
		if (viewerRenderer != null) viewerRenderer.render(guiGraphics, x, y);
	}

	public static List<GroupPreviewEntry> fromItems(List<ItemStack> items) {
		List<GroupPreviewEntry> result = new ArrayList<>(items.size());
		for (ItemStack item : items) result.add(ofItem(item));
		return List.copyOf(result);
	}

	@FunctionalInterface
	private interface Renderer {
		void render(GuiGraphics graphics, int x, int y);
	}

	@FunctionalInterface
	public interface PreviewRenderer {
		void render(GuiGraphics graphics, int x, int y);
	}
}
