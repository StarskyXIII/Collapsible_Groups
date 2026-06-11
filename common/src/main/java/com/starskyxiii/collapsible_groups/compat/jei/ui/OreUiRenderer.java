package com.starskyxiii.collapsible_groups.compat.jei.ui;

import com.starskyxiii.collapsible_groups.Constants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Texture-backed primitives for the Bedrock/Ore-inspired visual skin. */
public final class OreUiRenderer {
	private static final Identifier PANEL = sprite("ore_panel");
	private static final Identifier CARD = sprite("ore_card");
	private static final Identifier BUTTON = sprite("ore_button");
	private static final Identifier BUTTON_HOVER = sprite("ore_button_hover");
	private static final Identifier BUTTON_PRESSED = sprite("ore_button_pressed");
	private static final Identifier BUTTON_DISABLED = sprite("ore_button_disabled");
	private static final Identifier BUTTON_SELECTED = sprite("ore_button_selected");
	private static final Identifier BUTTON_SELECTED_HOVER = sprite("ore_button_selected_hover");
	private static final Identifier BUTTON_SELECTED_PRESSED = sprite("ore_button_selected_pressed");
	private static final Identifier SEGMENT = sprite("ore_segment");
	private static final Identifier SEGMENT_HOVER = sprite("ore_segment_hover");
	private static final Identifier SEGMENT_PRESSED = sprite("ore_segment_pressed");
	private static final Identifier SEGMENT_SELECTED = sprite("ore_segment_selected");
	private static final Identifier SEGMENT_SELECTED_HOVER = sprite("ore_segment_selected_hover");
	private static final Identifier SEGMENT_SELECTED_PRESSED = sprite("ore_segment_selected_pressed");
	private static final Identifier ICON_BUTTON = sprite("ore_icon_button");
	private static final Identifier ICON_BUTTON_HOVER = sprite("ore_icon_button_hover");
	private static final Identifier ICON_BUTTON_PRESSED = sprite("ore_icon_button_pressed");
	private static final Identifier ICON_BUTTON_DISABLED = sprite("ore_icon_button_disabled");
	private static final Identifier SWITCH_OFF = sprite("ore_switch_off");
	private static final Identifier SWITCH_OFF_HOVER = sprite("ore_switch_off_hover");
	private static final Identifier SWITCH_OFF_DISABLED = sprite("ore_switch_off_disabled");
	private static final Identifier SWITCH_ON = sprite("ore_switch_on");
	private static final Identifier SWITCH_ON_HOVER = sprite("ore_switch_on_hover");
	private static final Identifier SWITCH_ON_DISABLED = sprite("ore_switch_on_disabled");
	private static final Identifier SCROLLBAR_THUMB = sprite("ore_scrollbar_thumb");
	public static final Identifier ICON_EDIT = sprite("ore_icon_edit");
	public static final Identifier ICON_DELETE = sprite("ore_icon_delete");
	private static final int CONTROL_EDGE_DARK = 0xFF413F54;
	private static final int TOOLBAR_ICON_WIDTH = 16;
	private static final int TOOLBAR_BUTTON_WIDTH = 18;
	private static final int TOOLBAR_BUTTON_HEIGHT = 20;
	private static final int SWITCH_VISUAL_WIDTH = 22;
	private static final int SWITCH_VISUAL_HEIGHT = 12;

	private OreUiRenderer() {}

	private static Identifier sprite(String name) {
		return Identifier.fromNamespaceAndPath(Constants.MOD_ID, name);
	}

	private static void blitSprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int width, int height) {
		blitSprite(g, sprite, x, y, width, height, 0xFFFFFFFF);
	}

	private static void blitSprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int width, int height, int color) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, color);
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

	public static void drawScreenBars(GuiGraphicsExtractor g, int width, int height, int headerHeight, int footerHeight) {
		int footerY = height - footerHeight;
		g.fill(0, 0, width, headerHeight, OreUiPalette.SCREEN_BAR);
		g.fill(0, headerHeight, width, headerHeight + 1, OreUiPalette.DIVIDER);
		g.fill(0, footerY, width, height, OreUiPalette.SCREEN_BAR_SHADOW);
		g.fill(0, footerY, width, footerY + 1, OreUiPalette.DIVIDER);
	}

	public static void drawPanel(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		g.fill(x, y, x + width, y + height, OreUiPalette.SURFACE_DARK);
		blitSprite(g, PANEL, x, y, width, height);
	}

	public static void drawCard(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean hovered, int borderColor) {
		g.fill(x, y, x + width, y + height, OreUiPalette.SURFACE_DARK);
		blitSprite(g, CARD, x, y, width, height);
		if (hovered) {
			g.fill(x + 1, y + 1, x + width - 1, y + height - 1, OreUiPalette.CARD_BODY_HOVER);
		}
		drawOutline(g, x, y, width, height, borderColor);
	}

	public static void drawButton(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height,
	                              String label, ButtonState state) {
		int depth = buttonVisualDepth(state);
		Identifier sprite = buttonSprite(state);
		if (sprite != null) {
			blitSprite(g, sprite, x, y, width, height);
		} else {
			drawButtonFallback(g, x + 1, y + depth + 1, width - 2, height - depth - 2, state);
		}
		drawControlFrame(g, x, y, width, height, depth);
		int text = buttonTextColor(state);
		int yOffset = buttonTextOffset(state);
		String clipped = font.plainSubstrByWidth(label, Math.max(0, width - 4));
		g.text(font, clipped, x + Math.max(0, (width - font.width(clipped)) / 2),
			centeredTextY(font, y, height) + yOffset, text, false);
	}

	public static void drawSegment(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height,
	                               String label, ButtonState state) {
		int depth = buttonVisualDepth(state);
		Identifier sprite = segmentSprite(state);
		if (sprite != null) {
			blitSprite(g, sprite, x + 1, y + 1, width - 2, height - 2);
		} else {
			drawButtonFallback(g, x + 1, y + depth + 1, width - 2, height - depth - 2, state);
		}
		drawControlFrame(g, x, y, width, height, depth);
		int text = buttonTextColor(state);
		int yOffset = buttonTextOffset(state);
		String clipped = font.plainSubstrByWidth(label, Math.max(0, width - 4));
		g.text(font, clipped, x + Math.max(0, (width - font.width(clipped)) / 2),
			centeredTextY(font, y, height) + yOffset, text, false);
	}

	private static void drawControlFrame(GuiGraphicsExtractor g, int x, int y, int width, int height, int depth) {
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

	private static Identifier segmentSprite(ButtonState state) {
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

	public static void drawIconButton(GuiGraphicsExtractor g, int x, int y, int buttonSize,
	                                  Identifier icon, int iconSize, ButtonState state) {
		int depth = buttonVisualDepth(state);
		Identifier sprite = buttonSprite(state);
		if (sprite != null) {
			blitSprite(g, sprite, x, y, buttonSize, buttonSize);
		} else {
			drawButtonFallback(g, x + 1, y + depth + 1, buttonSize - 2, buttonSize - depth - 2, state);
		}
		drawControlFrame(g, x, y, buttonSize, buttonSize, depth);
		int iconX = x + Math.max(0, (buttonSize - iconSize) / 2);
		int iconY = y + Math.max(0, (buttonSize - iconSize) / 2) + buttonTextOffset(state);
		blitSprite(g, icon, iconX, iconY, iconSize, iconSize);
	}

	public static void drawToolbarIconButton(GuiGraphicsExtractor g, int x, int y, int width, int height,
	                                         Identifier icon, ButtonState state) {
		int yOffset = toolbarButtonOffset(state);
		int originX = x + Math.max(0, (width - TOOLBAR_ICON_WIDTH) / 2);
		int originY = y + Math.max(0, (height - TOOLBAR_BUTTON_HEIGHT) / 2);
		blitSprite(g, toolbarButtonSprite(state),
			originX - 1, originY + yOffset, TOOLBAR_BUTTON_WIDTH, TOOLBAR_BUTTON_HEIGHT);

		int iconColor = state == ButtonState.DISABLED ? 0x8CFFFFFF : 0xFFFFFFFF;
		blitSprite(g, icon, originX, originY + 1 + yOffset, TOOLBAR_ICON_WIDTH, TOOLBAR_ICON_WIDTH, iconColor);
	}

	private static Identifier buttonSprite(ButtonState state) {
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

	private static Identifier toolbarButtonSprite(ButtonState state) {
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

	private static void drawButtonFallback(GuiGraphicsExtractor g, int x, int y, int width, int height, ButtonState state) {
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

	public static void drawSwitch(GuiGraphicsExtractor g, int x, int y, int width, int height,
	                              boolean on, boolean active, boolean hovered, boolean pressed) {
		Identifier sprite = switchSprite(on, active, hovered || pressed);
		int visualX = x + (width - SWITCH_VISUAL_WIDTH) / 2;
		int visualY = y + (height - SWITCH_VISUAL_HEIGHT) / 2;
		blitSprite(g, sprite, visualX, visualY, SWITCH_VISUAL_WIDTH, SWITCH_VISUAL_HEIGHT);
	}

	private static Identifier switchSprite(boolean on, boolean active, boolean hovered) {
		if (!active) {
			return on ? SWITCH_ON_DISABLED : SWITCH_OFF_DISABLED;
		}
		if (hovered) {
			return on ? SWITCH_ON_HOVER : SWITCH_OFF_HOVER;
		}
		return on ? SWITCH_ON : SWITCH_OFF;
	}

	public static void drawScrollbarPixels(GuiGraphicsExtractor g, int x, int y, int height,
	                                       int visibleHeight, int contentHeight, int scrollOffset) {
		g.fill(x + 2, y, x + 4, y + height, OreUiPalette.SCROLLBAR_TRACK_LINE);
		if (contentHeight <= visibleHeight || contentHeight <= 0) {
			return;
		}
		int thumbHeight = Math.max(14, height * visibleHeight / contentHeight);
		int travel = height - thumbHeight;
		int thumbY = y + travel * scrollOffset / Math.max(1, contentHeight - visibleHeight);
		blitSprite(g, SCROLLBAR_THUMB, x, thumbY, 6, thumbHeight);
	}

	public static void drawMiniScrollbar(GuiGraphicsExtractor g, int x, int y, int height,
	                                     int visibleRows, int totalRows, int rowOffset) {
		g.fill(x + 2, y, x + 3, y + height, OreUiPalette.SCROLLBAR_TRACK_LINE);
		if (totalRows <= visibleRows || totalRows <= 0) {
			return;
		}
		int thumbHeight = Math.max(8, height * visibleRows / totalRows);
		int travel = height - thumbHeight;
		int maxRow = Math.max(1, totalRows - visibleRows);
		int thumbY = y + travel * rowOffset / maxRow;
		blitSprite(g, SCROLLBAR_THUMB, x, thumbY, 5, thumbHeight);
	}

	public static void drawSlot(GuiGraphicsExtractor g, int x, int y, int size) {
		g.fill(x, y, x + size, y + size, OreUiPalette.SURFACE_DARK);
		drawOutline(g, x, y, size, size, OreUiPalette.OUTLINE_DARK);
	}

	/** Draws a slot grid with shared 1px lines; {@code cellPitch} = cell interior + 1, total size = cols/rows * pitch + 1. */
	public static void drawSlotGrid(GuiGraphicsExtractor g, int x, int y, int cols, int rows, int cellPitch) {
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

	public static void drawOutline(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
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
