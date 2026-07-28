package com.starskyxiii.collapsible_groups.viewer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Viewer-neutral, render-ready value used by manager preview snapshots. */
public record ViewerPreviewValue(@Nullable ItemStack itemStack, Renderer renderer) {
	public ViewerPreviewValue {
		Objects.requireNonNull(renderer, "renderer");
	}

	public static ViewerPreviewValue item(ItemStack stack) {
		Objects.requireNonNull(stack, "stack");
		return new ViewerPreviewValue(stack, (graphics, x, y) -> graphics.renderItem(stack, x, y));
	}

	public static ViewerPreviewValue rendered(Renderer renderer) {
		return new ViewerPreviewValue(null, renderer);
	}

	@FunctionalInterface
	public interface Renderer {
		void render(GuiGraphics graphics, int x, int y);
	}
}
