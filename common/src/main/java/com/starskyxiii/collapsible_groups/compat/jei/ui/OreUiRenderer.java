package com.starskyxiii.collapsible_groups.compat.jei.ui;

import com.starskyxiii.collapsible_groups.Constants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

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
	/** Height of the toolbar icon-button sprite set (distinct from {@link #BUTTON_DESIGN_HEIGHT}). */
	private static final int TOOLBAR_BUTTON_HEIGHT = 20;
	private static final int SWITCH_VISUAL_WIDTH = 22;
	private static final int SWITCH_VISUAL_HEIGHT = 12;

	/**
	 * Design height of the {@code ore_button}/{@code ore_icon_button} sprite sets.
	 * Rendering at any other height stretches the sprite and degrades the frame,
	 * which is the recurring root cause of the "missing button border" regressions.
	 * Callers must size buttons to this height; mismatches emit a one-time warning.
	 */
	public static final int BUTTON_DESIGN_HEIGHT = 20;

	/** De-duplicates non-standard button-size warnings; key = {@code ((long) width << 32) | (height & 0xffffffffL)}. */
	private static final Set<Long> WARNED_BUTTON_SIZES = new HashSet<>();

	private OreUiRenderer() {}

	private static void warnNonDesignHeight(String primitive, int width, int height) {
		if (height == BUTTON_DESIGN_HEIGHT) {
			return;
		}
		long key = ((long) width << 32) | (height & 0xffffffffL);
		if (WARNED_BUTTON_SIZES.add(key)) {
			Constants.LOG.warn("{} rendered at {}x{} but design height is {}; sprite frame will degrade",
				primitive, width, height, BUTTON_DESIGN_HEIGHT);
		}
	}

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
		warnNonDesignHeight("drawButton", width, height);
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
		int depth = segmentVisualDepth(state);
		ResourceLocation sprite = segmentSprite(state);
		if (sprite != null) {
			g.blitSprite(sprite, x + 1, y + 1, width - 2, height - 2);
		} else {
			drawButtonFallback(g, x + 1, y + depth + 1, width - 2, height - depth - 2, state);
		}
		drawControlFrame(g, x, y, width, height, depth);
		int text = buttonTextColor(state);
		int yOffset = segmentTextOffset(state);
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

	private static int segmentVisualDepth(ButtonState state) {
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
		warnNonDesignHeight("drawIconButton", buttonSize, buttonSize);
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
			case NORMAL, DISABLED -> -1;
			case HOVERED -> 0;
			case PRESSED, SELECTED, SELECTED_HOVERED, SELECTED_PRESSED -> 1;
		};
	}

	private static int segmentTextOffset(ButtonState state) {
		return switch (state) {
			case NORMAL, DISABLED -> -1;
			case HOVERED -> 0;
			case PRESSED, SELECTED, SELECTED_HOVERED, SELECTED_PRESSED -> 1;
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

	// ── Bedrock-style slider (P5-polish-8) ─────────────────────────────────
	// Single authority for the slider visual language: thin track, accent left
	// fill, and a knob that replicates the tactile button look (drawButton's
	// nine-slice sprite is designed at height 20 and degrades below it, so the
	// knob is drawn with plain fills instead).

	public static final int SLIDER_TRACK_HEIGHT = 4;
	public static final int SLIDER_KNOB_WIDTH = 14;
	public static final int SLIDER_KNOB_HEIGHT = 14;
	public static final int SLIDER_KNOB_HALF = SLIDER_KNOB_WIDTH / 2;
	private static final int SLIDER_TRACK_UNFILLED = 0xFF2B2D31;

	/**
	 * Knob rect for a track spanning {@code [x, x+width)}; {@code y} is the top of
	 * the knob band. The knob center follows {@code value/max} along the track and
	 * may overhang each track end by {@link #SLIDER_KNOB_HALF}.
	 */
	public static EditorChrome.Rect sliderKnobRect(int x, int y, int width, int value, int max) {
		int clamped = Math.max(0, Math.min(max, value));
		int center = x + (int) ((long) width * clamped / Math.max(1, max));
		int knobX = Math.min(Math.max(center - SLIDER_KNOB_HALF, x - SLIDER_KNOB_HALF),
			x + width - SLIDER_KNOB_HALF);
		return new EditorChrome.Rect(knobX, y, SLIDER_KNOB_WIDTH, SLIDER_KNOB_HEIGHT);
	}

	/** Hover/click band: the track x-range extended by the knob overhang on both sides. */
	public static EditorChrome.Rect sliderHitBand(int x, int y, int width) {
		return new EditorChrome.Rect(x - SLIDER_KNOB_HALF, y, width + SLIDER_KNOB_WIDTH, SLIDER_KNOB_HEIGHT);
	}

	/**
	 * Draws the full slider (track + fill + knob) and returns the knob rect.
	 * {@code y} is the top of the {@link #SLIDER_KNOB_HEIGHT}-tall knob band; the
	 * track is vertically centered inside it.
	 *
	 * <p>Knob anatomy, outside-in (total 14px tall): 1px dark frame + a face with
	 * a 1px light inner ring on all four sides (12×10 region) + a 2px dark bottom
	 * bevel (12×2) + the frame's 1px bottom edge = 14.
	 */
	public static EditorChrome.Rect drawSlider(GuiGraphics g, int x, int y, int width, int value, int max,
	                                           boolean active, boolean hot) {
		int trackY = y + (SLIDER_KNOB_HEIGHT - SLIDER_TRACK_HEIGHT) / 2;
		int clamped = Math.max(0, Math.min(max, value));
		g.fill(x, trackY, x + width, trackY + SLIDER_TRACK_HEIGHT,
			active ? SLIDER_TRACK_UNFILLED : OreUiPalette.SURFACE_DISABLED);
		g.fill(x, trackY, x + Math.max(1, (int) ((long) width * clamped / Math.max(1, max))),
			trackY + SLIDER_TRACK_HEIGHT,
			active ? OreUiPalette.BUTTON_PRIMARY : OreUiPalette.OUTLINE_DISABLED);
		drawOutline(g, x, trackY, width, SLIDER_TRACK_HEIGHT,
			hot ? OreUiPalette.OUTLINE_HOVER : OreUiPalette.OUTLINE_DARK);

		EditorChrome.Rect knob = sliderKnobRect(x, y, width, value, max);
		if (active) {
			int kx = knob.x();
			int ky = knob.y();
			int kRight = knob.right();
			int kBottom = knob.bottom();
			// Face (inset 1px from the frame, minus the 2px bottom bevel) …
			g.fill(kx + 1, ky + 1, kRight - 1, kBottom - 3,
				hot ? OreUiPalette.BUTTON_LIGHT_HOVER : OreUiPalette.BUTTON_LIGHT);
			// … with its light inner ring on all four sides …
			drawOutline(g, kx + 1, ky + 1, knob.width() - 2, knob.height() - 4, OreUiPalette.BUTTON_LIGHT_TOP);
			// … the dark bottom bevel …
			g.fill(kx + 1, kBottom - 3, kRight - 1, kBottom - 1, OreUiPalette.BUTTON_LIGHT_BOTTOM);
			// … and the dark frame (disjoint from the inset regions above).
			drawOutline(g, kx, ky, knob.width(), knob.height(), OreUiPalette.OUTLINE_DARK);
			if (hot) {
				// Bedrock-style focus ring just outside the frame.
				drawOutline(g, kx - 1, ky - 1, knob.width() + 2, knob.height() + 2, OreUiPalette.OUTLINE_HOVER);
			}
		}
		return knob;
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

	/**
	 * Amber "shown elsewhere by priority" accent (shared with the icon picker's
	 * non-group corner tab). Neutral, not a block colour.
	 */
	public static final int OVERLAP_ACCENT = 0xFFF2C744;

	/** Faint amber frame drawn around an overlap source cell (1px). */
	private static final int OVERLAP_FRAME = 0x66F2C744;

	/**
	 * Overlap marker for a source cell whose JEI winner is another group: a faint
	 * amber 1px frame plus an amber right-top triangle corner tab (matching the
	 * icon picker's non-group language). Draw <em>after</em> {@code renderItem};
	 * callers must raise z above the ingredient depth first (pushPose/translate),
	 * since the ingredient renders at depth ~150 and plain fills would sit under it.
	 *
	 * @param x   left of the 16px icon region
	 * @param y   top of the 16px icon region
	 * @param size icon region size (typically 16)
	 */
	public static void drawOverlapMarker(GuiGraphics g, int x, int y, int size) {
		drawOutline(g, x, y, size, size, OVERLAP_FRAME);
		drawCornerMarker(g, x, y, size, OVERLAP_ACCENT);
	}

	/** Faint green frame drawn around a selected / rule-covered source cell (1px). */
	private static final int SELECTED_FRAME = 0x6670B95A;

	/**
	 * Selected-in-current-group marker for a source cell: a faint green 1px frame
	 * plus a green right-top triangle corner tab. Symmetric to
	 * {@link #drawOverlapMarker} (same frame/tab shape, green instead of amber),
	 * shared by both explicit selections and rule-covered cells. Draw <em>after</em>
	 * {@code renderItem}; callers must raise z above the ingredient depth first
	 * (pushPose/translate), since the ingredient renders at depth ~150 and plain
	 * fills would sit under it.
	 *
	 * @param x    left of the icon region
	 * @param y    top of the icon region
	 * @param size icon region size (typically 16)
	 */
	public static void drawSelectedMarker(GuiGraphics g, int x, int y, int size) {
		drawOutline(g, x, y, size, size, SELECTED_FRAME);
		drawCornerMarker(g, x, y, size, OreUiPalette.OUTLINE_SELECTED);
	}

	/**
	 * Generic right-top triangle corner tab, used to flag a source cell's state
	 * (overlap amber, selected-in-current-group green, ...) without a full-cell
	 * tint. Draw <em>after</em> {@code renderItem}; callers must raise z above the
	 * ingredient depth first (pushPose/translate), since the ingredient renders at
	 * depth ~150 and plain fills would sit under it.
	 *
	 * @param x     left of the icon region
	 * @param y     top of the icon region
	 * @param size  icon region size (typically 16)
	 * @param color ARGB colour of the tab
	 */
	public static void drawCornerMarker(GuiGraphics g, int x, int y, int size, int color) {
		int right = x + size;
		// Right-top triangle tab: rows shrink from the right edge inward.
		for (int row = 0; row < CORNER_MARKER_SIZE; row++) {
			int rowWidth = CORNER_MARKER_SIZE - row;
			g.fill(right - rowWidth, y + row, right, y + row + 1, color);
		}
	}

	private static final int CORNER_MARKER_SIZE = 5;

	/** Size of the hover remove-× badge on right-panel preview cells. */
	public static final int REMOVE_BADGE_SIZE = 9;
	private static final int REMOVE_BADGE_BG = 0xFFCA3636;
	private static final int REMOVE_BADGE_BORDER = 0xFF1E1E1F;
	private static final int REMOVE_BADGE_MARK = 0xFFFFFFFF;

	/**
	 * Hover remove-× badge for a right-panel preview cell (P0: removal only via a
	 * discrete × hot-zone, never the whole cell). Anchored top-right of the 16px
	 * icon region, overhanging by 1px. Draw <em>after</em> {@code renderItem} with
	 * z raised above the ingredient depth (~150).
	 *
	 * @param iconX left of the 16px icon region
	 * @param iconY top of the 16px icon region
	 */
	public static void drawRemoveBadge(GuiGraphics g, int iconX, int iconY, boolean hovered) {
		EditorChrome.Rect r = removeBadgeRect(iconX, iconY);
		int bx = r.x();
		int by = r.y();
		int bRight = r.right();
		int bBottom = r.bottom();
		g.fill(bx, by, bRight, bBottom, REMOVE_BADGE_BG);
		drawOutline(g, bx, by, r.width(), r.height(), REMOVE_BADGE_BORDER);
		if (hovered) {
			drawOutline(g, bx, by, r.width(), r.height(), OreUiPalette.OUTLINE_HOVER);
		}
		// Two diagonals forming the ×, inset 2px from the badge edges.
		int x0 = bx + 2;
		int y0 = by + 2;
		int span = REMOVE_BADGE_SIZE - 4;
		for (int i = 0; i < span; i++) {
			g.fill(x0 + i, y0 + i, x0 + i + 1, y0 + i + 1, REMOVE_BADGE_MARK);
			g.fill(x0 + (span - 1 - i), y0 + i, x0 + (span - 1 - i) + 1, y0 + i + 1, REMOVE_BADGE_MARK);
		}
	}

	/**
	 * Hit-zone / draw rect for the remove-× badge, flush with the top-right corner
	 * of the 16px icon region (kept inside the cell so hover hit-testing never
	 * attributes the badge to a neighbouring cell).
	 */
	public static EditorChrome.Rect removeBadgeRect(int iconX, int iconY) {
		int bx = iconX + 16 - REMOVE_BADGE_SIZE;
		return new EditorChrome.Rect(bx, iconY, REMOVE_BADGE_SIZE, REMOVE_BADGE_SIZE);
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

	public static int textFieldTextY(Font font, int top, int height) {
		return top + Math.max(0, (height - font.lineHeight) / 2);
	}
}
