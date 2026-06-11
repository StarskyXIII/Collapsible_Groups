package com.starskyxiii.collapsible_groups.compat.jei.ui;

import com.starskyxiii.collapsible_groups.Constants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Texture-backed primitives for the Bedrock/Ore-inspired visual skin. */
public final class OreUiRenderer {
	private static final ResourceLocation PANEL = sprite("ore_panel");
	private static final ResourceLocation CARD = sprite("ore_card");
	private static final ResourceLocation BUTTON = sprite("ore_button");
	private static final ResourceLocation BUTTON_HOVER = sprite("ore_button_hover");
	private static final ResourceLocation BUTTON_PRESSED = sprite("ore_button_pressed");
	private static final ResourceLocation BUTTON_DISABLED = sprite("ore_button_disabled");
	private static final ResourceLocation BUTTON_SELECTED = sprite("ore_button_selected");
	private static final ResourceLocation BUTTON_SELECTED_HOVER = sprite("ore_button_selected_hover");
	private static final ResourceLocation BUTTON_SELECTED_PRESSED = sprite("ore_button_selected_pressed");
	private static final ResourceLocation SEGMENT = sprite("ore_segment");
	private static final ResourceLocation SEGMENT_HOVER = sprite("ore_segment_hover");
	private static final ResourceLocation SEGMENT_PRESSED = sprite("ore_segment_pressed");
	private static final ResourceLocation SEGMENT_SELECTED = sprite("ore_segment_selected");
	private static final ResourceLocation SEGMENT_SELECTED_HOVER = sprite("ore_segment_selected_hover");
	private static final ResourceLocation SEGMENT_SELECTED_PRESSED = sprite("ore_segment_selected_pressed");
	private static final ResourceLocation ICON_BUTTON = sprite("ore_icon_button");
	private static final ResourceLocation ICON_BUTTON_HOVER = sprite("ore_icon_button_hover");
	private static final ResourceLocation ICON_BUTTON_PRESSED = sprite("ore_icon_button_pressed");
	private static final ResourceLocation ICON_BUTTON_DISABLED = sprite("ore_icon_button_disabled");
	private static final ResourceLocation SWITCH_OFF = sprite("ore_switch_off");
	private static final ResourceLocation SWITCH_OFF_HOVER = sprite("ore_switch_off_hover");
	private static final ResourceLocation SWITCH_OFF_DISABLED = sprite("ore_switch_off_disabled");
	private static final ResourceLocation SWITCH_ON = sprite("ore_switch_on");
	private static final ResourceLocation SWITCH_ON_HOVER = sprite("ore_switch_on_hover");
	private static final ResourceLocation SWITCH_ON_DISABLED = sprite("ore_switch_on_disabled");
	private static final ResourceLocation SCROLLBAR_THUMB = sprite("ore_scrollbar_thumb");
	public static final ResourceLocation ICON_EDIT = sprite("ore_icon_edit");
	public static final ResourceLocation ICON_DELETE = sprite("ore_icon_delete");
	private static final int CONTROL_EDGE_DARK = 0xFF413F54;
	private static final int TOOLBAR_ICON_WIDTH = 16;
	private static final int TOOLBAR_BUTTON_WIDTH = 18;
	private static final int TOOLBAR_BUTTON_HEIGHT = 20;
	private static final int SWITCH_VISUAL_WIDTH = 22;
	private static final int SWITCH_VISUAL_HEIGHT = 12;

	private OreUiRenderer() {}

	private static ResourceLocation sprite(String name) {
		return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, name);
	}

	public enum ButtonState {
		NORMAL,
		HOVERED,
		PRESSED,
		DISABLED,
		SELECTED,
		SELECTED_HOVERED,
		SELECTED_PRESSED
	}

	public static void drawScreenBars(GuiGraphics g, int width, int height, int headerHeight, int footerHeight) {
		int footerY = height - footerHeight;
		g.fill(0, 0, width, headerHeight, OreUiPalette.SCREEN_BAR);
		g.fill(0, headerHeight, width, headerHeight + 1, OreUiPalette.DIVIDER);
		g.fill(0, footerY, width, height, OreUiPalette.SCREEN_BAR_SHADOW);
		g.fill(0, footerY, width, footerY + 1, OreUiPalette.DIVIDER);
	}

	public static void drawPanel(GuiGraphics g, int x, int y, int width, int height) {
		g.fill(x, y, x + width, y + height, OreUiPalette.SURFACE_DARK);
		g.blitSprite(PANEL, x, y, width, height);
	}

	public static void drawCard(GuiGraphics g, int x, int y, int width, int height, boolean hovered, int borderColor) {
		g.fill(x, y, x + width, y + height, OreUiPalette.SURFACE_DARK);
		g.blitSprite(CARD, x, y, width, height);
		if (hovered) {
			g.fill(x + 1, y + 1, x + width - 1, y + height - 1, OreUiPalette.CARD_BODY_HOVER);
		}
		drawOutline(g, x, y, width, height, borderColor);
	}

	public static void drawButton(GuiGraphics g, Font font, int x, int y, int width, int height,
	                              String label, ButtonState state) {
		int depth = buttonVisualDepth(state);
		ResourceLocation sprite = buttonSprite(state);
		if (sprite != null) {
			g.blitSprite(sprite, x, y, width, height);
		} else {
			drawButtonFallback(g, x + 1, y + depth + 1, width - 2, height - depth - 2, state);
		}
		drawControlFrame(g, x, y, width, height, depth);
		int text = buttonTextColor(state);
		int yOffset = buttonTextOffset(state);
		String clipped = font.plainSubstrByWidth(label, Math.max(0, width - 4));
		g.drawString(font, clipped, x + Math.max(0, (width - font.width(clipped)) / 2),
			centeredTextY(font, y, height) + yOffset, text, false);
	}

	public static void drawSegment(GuiGraphics g, Font font, int x, int y, int width, int height,
	                               String label, ButtonState state) {
		int depth = buttonVisualDepth(state);
		ResourceLocation sprite = segmentSprite(state);
		if (sprite != null) {
			g.blitSprite(sprite, x + 1, y + 1, width - 2, height - 2);
		} else {
			drawButtonFallback(g, x + 1, y + depth + 1, width - 2, height - depth - 2, state);
		}
		drawControlFrame(g, x, y, width, height, depth);
		int text = buttonTextColor(state);
		int yOffset = buttonTextOffset(state);
		String clipped = font.plainSubstrByWidth(label, Math.max(0, width - 4));
		g.drawString(font, clipped, x + Math.max(0, (width - font.width(clipped)) / 2),
			centeredTextY(font, y, height) + yOffset, text, false);
	}

	private static void drawControlFrame(GuiGraphics g, int x, int y, int width, int height, int depth) {
		int right = x + width;
		int bottom = y + height;
		int top = y + depth;
		g.fill(x, top, right, top + 1, CONTROL_EDGE_DARK);
		g.fill(x, top + 1, x + 1, bottom, CONTROL_EDGE_DARK);
		g.fill(right - 1, top + 1, right, bottom, CONTROL_EDGE_DARK);
		g.fill(x, bottom - 1, right, bottom, CONTROL_EDGE_DARK);
	}

	private static int buttonVisualDepth(ButtonState state) {
		return switch (state) {
			case HOVERED -> 1;
			case PRESSED, SELECTED, SELECTED_HOVERED, SELECTED_PRESSED -> 2;
			case NORMAL, DISABLED -> 0;
		};
	}

	private static ResourceLocation segmentSprite(ButtonState state) {
		return switch (state) {
			case NORMAL -> SEGMENT;
			case HOVERED -> SEGMENT_HOVER;
			case PRESSED -> SEGMENT_PRESSED;
			case DISABLED -> SEGMENT;
			case SELECTED -> SEGMENT_SELECTED;
			case SELECTED_HOVERED -> SEGMENT_SELECTED_HOVER;
			case SELECTED_PRESSED -> SEGMENT_SELECTED_PRESSED;
		};
	}

	public static void drawIconButton(GuiGraphics g, int x, int y, int buttonSize,
	                                  ResourceLocation icon, int iconSize, ButtonState state) {
		int depth = buttonVisualDepth(state);
		ResourceLocation sprite = buttonSprite(state);
		if (sprite != null) {
			g.blitSprite(sprite, x, y, buttonSize, buttonSize);
		} else {
			drawButtonFallback(g, x + 1, y + depth + 1, buttonSize - 2, buttonSize - depth - 2, state);
		}
		drawControlFrame(g, x, y, buttonSize, buttonSize, depth);
		int iconX = x + Math.max(0, (buttonSize - iconSize) / 2);
		int iconY = y + Math.max(0, (buttonSize - iconSize) / 2) + buttonTextOffset(state);
		g.blitSprite(icon, iconX, iconY, iconSize, iconSize);
	}

	public static void drawToolbarIconButton(GuiGraphics g, int x, int y, int width, int height,
	                                         ResourceLocation icon, ButtonState state) {
		int yOffset = toolbarButtonOffset(state);
		int originX = x + Math.max(0, (width - TOOLBAR_ICON_WIDTH) / 2);
		int originY = y + Math.max(0, (height - TOOLBAR_BUTTON_HEIGHT) / 2);
		g.blitSprite(toolbarButtonSprite(state),
			originX - 1, originY + yOffset, TOOLBAR_BUTTON_WIDTH, TOOLBAR_BUTTON_HEIGHT);

		if (state == ButtonState.DISABLED) {
			g.setColor(1.0F, 1.0F, 1.0F, 0.55F);
		}
		g.blitSprite(icon, originX, originY + 1 + yOffset, TOOLBAR_ICON_WIDTH, TOOLBAR_ICON_WIDTH);
		if (state == ButtonState.DISABLED) {
			g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		}
	}

	private static ResourceLocation buttonSprite(ButtonState state) {
		return switch (state) {
			case NORMAL -> BUTTON;
			case HOVERED -> BUTTON_HOVER;
			case PRESSED -> BUTTON_PRESSED;
			case DISABLED -> BUTTON_DISABLED;
			case SELECTED -> BUTTON_SELECTED;
			case SELECTED_HOVERED -> BUTTON_SELECTED_HOVER;
			case SELECTED_PRESSED -> BUTTON_SELECTED_PRESSED;
		};
	}

	private static ResourceLocation toolbarButtonSprite(ButtonState state) {
		return switch (state) {
			case DISABLED -> ICON_BUTTON_DISABLED;
			case PRESSED, SELECTED_PRESSED -> ICON_BUTTON_PRESSED;
			case HOVERED, SELECTED, SELECTED_HOVERED -> ICON_BUTTON_HOVER;
			case NORMAL -> ICON_BUTTON;
		};
	}

	private static int toolbarButtonOffset(ButtonState state) {
		return switch (state) {
			case HOVERED, PRESSED, SELECTED, SELECTED_HOVERED, SELECTED_PRESSED -> 1;
			case NORMAL, DISABLED -> 0;
		};
	}

	private static void drawButtonFallback(GuiGraphics g, int x, int y, int width, int height, ButtonState state) {
		if (width <= 0 || height <= 0) {
			return;
		}
		int fill = switch (state) {
			case NORMAL -> OreUiPalette.BUTTON_LIGHT;
			case HOVERED -> OreUiPalette.BUTTON_LIGHT_HOVER;
			case PRESSED -> OreUiPalette.BUTTON_LIGHT_PRESSED;
			case DISABLED -> OreUiPalette.BUTTON_LIGHT_DISABLED;
			case SELECTED -> OreUiPalette.BUTTON_PRIMARY;
			case SELECTED_HOVERED -> OreUiPalette.BUTTON_PRIMARY_HOVER;
			case SELECTED_PRESSED -> OreUiPalette.BUTTON_PRIMARY_PRESSED;
		};
		g.fill(x, y, x + width, y + height, fill);
	}

	private static int buttonTextColor(ButtonState state) {
		return switch (state) {
			case DISABLED -> 0xFF413F54;
			case HOVERED, PRESSED, SELECTED, SELECTED_HOVERED, SELECTED_PRESSED -> 0xFF314A60;
			case NORMAL -> OreUiPalette.TEXT_DARK;
		};
	}

	private static int buttonTextOffset(ButtonState state) {
		return switch (state) {
			case NORMAL -> -1;
			case HOVERED -> 0;
			case PRESSED, SELECTED, SELECTED_HOVERED, SELECTED_PRESSED, DISABLED -> 1;
		};
	}

	public static void drawSwitch(GuiGraphics g, int x, int y, int width, int height,
	                              boolean on, boolean active, boolean hovered, boolean pressed) {
		ResourceLocation sprite = switchSprite(on, active, hovered || pressed);
		int visualX = x + (width - SWITCH_VISUAL_WIDTH) / 2;
		int visualY = y + (height - SWITCH_VISUAL_HEIGHT) / 2;
		g.blitSprite(sprite, visualX, visualY, SWITCH_VISUAL_WIDTH, SWITCH_VISUAL_HEIGHT);
	}

	private static ResourceLocation switchSprite(boolean on, boolean active, boolean hovered) {
		if (!active) {
			return on ? SWITCH_ON_DISABLED : SWITCH_OFF_DISABLED;
		}
		if (hovered) {
			return on ? SWITCH_ON_HOVER : SWITCH_OFF_HOVER;
		}
		return on ? SWITCH_ON : SWITCH_OFF;
	}

	public static void drawScrollbarPixels(GuiGraphics g, int x, int y, int height,
	                                       int visibleHeight, int contentHeight, int scrollOffset) {
		g.fill(x + 2, y, x + 4, y + height, OreUiPalette.SCROLLBAR_TRACK_LINE);
		if (contentHeight <= visibleHeight || contentHeight <= 0) {
			return;
		}
		int thumbHeight = Math.max(14, height * visibleHeight / contentHeight);
		int travel = height - thumbHeight;
		int thumbY = y + travel * scrollOffset / Math.max(1, contentHeight - visibleHeight);
		g.blitSprite(SCROLLBAR_THUMB, x, thumbY, 6, thumbHeight);
	}

	public static void drawMiniScrollbar(GuiGraphics g, int x, int y, int height,
	                                     int visibleRows, int totalRows, int rowOffset) {
		g.fill(x + 2, y, x + 3, y + height, OreUiPalette.SCROLLBAR_TRACK_LINE);
		if (totalRows <= visibleRows || totalRows <= 0) {
			return;
		}
		int thumbHeight = Math.max(8, height * visibleRows / totalRows);
		int travel = height - thumbHeight;
		int maxRow = Math.max(1, totalRows - visibleRows);
		int thumbY = y + travel * rowOffset / maxRow;
		g.blitSprite(SCROLLBAR_THUMB, x, thumbY, 5, thumbHeight);
	}

	public static void drawSlot(GuiGraphics g, int x, int y, int size) {
		g.fill(x, y, x + size, y + size, OreUiPalette.SURFACE_DARK);
		drawOutline(g, x, y, size, size, OreUiPalette.OUTLINE_DARK);
	}

	/** Draws a slot grid with shared 1px lines; {@code cellPitch} = cell interior + 1, total size = cols/rows * pitch + 1. */
	public static void drawSlotGrid(GuiGraphics g, int x, int y, int cols, int rows, int cellPitch) {
		int width = cols * cellPitch + 1;
		int height = rows * cellPitch + 1;
		g.fill(x, y, x + width, y + height, OreUiPalette.SURFACE_DARK);
		for (int col = 0; col <= cols; col++) {
			int lineX = x + col * cellPitch;
			g.fill(lineX, y, lineX + 1, y + height, OreUiPalette.OUTLINE_DARK);
		}
		for (int row = 0; row <= rows; row++) {
			int lineY = y + row * cellPitch;
			g.fill(x, lineY, x + width, lineY + 1, OreUiPalette.OUTLINE_DARK);
		}
	}

	public static void drawOutline(GuiGraphics g, int x, int y, int width, int height, int color) {
		int right = x + width;
		int bottom = y + height;
		g.fill(x, y, right, y + 1, color);
		g.fill(x, bottom - 1, right, bottom, color);
		g.fill(x, y + 1, x + 1, bottom - 1, color);
		g.fill(right - 1, y + 1, right, bottom - 1, color);
	}

	public static int centeredTextY(Font font, int top, int height) {
		return top + Math.max(0, (height - font.lineHeight) / 2) + 1;
	}
}
