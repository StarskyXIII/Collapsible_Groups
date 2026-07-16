package com.starskyxiii.collapsible_groups.client.widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry for the editor settings-mode row list.
 *
 * <p>Holds no UI state: {@link #compute} takes the panel content rect plus the
 * current scroll offset and returns the full ordered list of rows, each with an
 * absolute (already scroll-adjusted) row rect and the relative-then-absolute
 * child-control rects it needs. Both the render pass and the hit-test pass
 * consume the same layout so the two can never drift.
 *
 * <p>{@link Result#contentHeight()} is the full unscrolled content height,
 * including section subheaders and per-row subtext, so the caller can clamp the
 * scroll offset and size the pixel scrollbar thumb.
 */
public final class SettingsRowLayout {
	/** Button/switch design height; must match {@link UiSkinRenderer#BUTTON_DESIGN_HEIGHT}. */
	public static final int BUTTON_HEIGHT = UiSkinRenderer.BUTTON_DESIGN_HEIGHT;

	public static final int ROW_GAP = 3;
	public static final int SECTION_GAP = 8;
	public static final int SUBHEADER_HEIGHT = 20;
	/**
	 * Vertical breathing room below the last row of each section.
	 * Read by both {@link #compute} (folded into {@link Result#contentHeight()} so
	 * scrolling to the bottom does not clip the final section) and the Screen's
	 * section backdrop (last-row bottom + this pad). The two must stay in sync.
	 */
	public static final int SECTION_BOTTOM_PAD = 6;
	public static final int ICON_ROW_HEIGHT = 28;
	public static final int COLOR_ROW_HEIGHT = 24;
	public static final int BEHAVIOR_ROW_HEIGHT = 24;
	public static final int PRIORITY_ROW_HEIGHT = 32;
	public static final int ID_ROW_HEIGHT = 32;

	public static final int ICON_SLOT_SIZE = 20;
	public static final int SWATCH_SIZE = 14;
	public static final int WIDE_BUTTON_WIDTH = 42;
	public static final int ICON_BUTTON_WIDTH = 20;
	public static final int HEX_BOX_WIDTH = 72;
	public static final int COLOR_SWATCH_GAP = 5;
	public static final int PRIORITY_VALUE_WIDTH = 40;
	public static final int SWITCH_WIDTH = 44;
	public static final int PAD = 6;

	public enum Kind {
		SUBHEADER,
		ICON,
		SWAP,
		COLOR,
		PRIORITY,
		ID,
		ENABLED
	}

	private SettingsRowLayout() {}

	/** One laid-out row. All rects are absolute (scroll offset already subtracted). */
	public record Row(
		Kind kind,
		Rect rect,
		Object payload,
		Rect slot,
		Rect changeBtn,
		Rect clearBtn,
		Rect swatch,
		Rect hexBox,
		Rect pickerBtn,
		Rect resetBtn,
		Rect stepMinus,
		Rect stepPlus,
		Rect valueBox,
		Rect copyBtn,
		Rect switchRect,
		Rect swapBtn
	) {}

	public record Rect(int x, int y, int width, int height) {
		public int right() {
			return x + width;
		}

		public int bottom() {
			return y + height;
		}

		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
		}
	}

	public record Result(List<Row> rows, int contentHeight) {}

	/**
	 * Lays out the settings rows.
	 *
	 * @param x            left of the content column
	 * @param topY         top of the content column (already scroll-anchored origin)
	 * @param w            content column width
	 * @param scrollOffset current scroll offset in pixels (subtracted from every rect)
	 * @param colorTargets ordinal-indexed color targets (payload for COLOR rows)
	 */
	public static Result compute(int x, int topY, int w, int scrollOffset, Object[] colorTargets) {
		List<Row> rows = new ArrayList<>();
		int y = topY - scrollOffset;
		final int contentTop = y;

		// --- Appearance: icons ---
		y = subheader(rows, x, y, w, "appearance.icons");
		y = iconRow(rows, x, y, w, Boolean.FALSE);
		y += ICON_ROW_HEIGHT + ROW_GAP;
		y = iconRow(rows, x, y, w, Boolean.TRUE);
		y += ICON_ROW_HEIGHT + ROW_GAP;
		y = swapRow(rows, x, y, w);
		y += BEHAVIOR_ROW_HEIGHT + SECTION_GAP;

		// --- Appearance: colors ---
		y = subheader(rows, x, y, w, "appearance.colors");
		for (Object target : colorTargets) {
			y = colorRow(rows, x, y, w, target);
			y += COLOR_ROW_HEIGHT + ROW_GAP;
		}
		y += SECTION_GAP - ROW_GAP;

		// --- Behavior: Enabled → Priority → Group ID ---
		y = subheader(rows, x, y, w, "behavior");
		y = enabledRow(rows, x, y, w);
		y += BEHAVIOR_ROW_HEIGHT + ROW_GAP;
		y = priorityRow(rows, x, y, w);
		y += PRIORITY_ROW_HEIGHT + ROW_GAP;
		y = idRow(rows, x, y, w);
		y += ID_ROW_HEIGHT;

		// Fold the section bottom pad into the scrollable content height so
		// the final section keeps its breathing room when scrolled to the bottom.
		int contentHeight = y - contentTop + SECTION_BOTTOM_PAD;
		return new Result(rows, contentHeight);
	}

	private static int subheader(List<Row> rows, int x, int y, int w, String key) {
		rows.add(row(Kind.SUBHEADER, new Rect(x, y, w, SUBHEADER_HEIGHT), key));
		return y + SUBHEADER_HEIGHT + ROW_GAP;
	}

	private static int iconRow(List<Row> rows, int x, int y, int w, Boolean back) {
		Rect rect = new Rect(x, y, w, ICON_ROW_HEIGHT);
		int by = y + (ICON_ROW_HEIGHT - BUTTON_HEIGHT) / 2;
		Rect clear = new Rect(x + w - PAD - WIDE_BUTTON_WIDTH, by, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT);
		Rect change = new Rect(clear.x() - ROW_GAP - WIDE_BUTTON_WIDTH, by, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT);
		Rect slot = new Rect(x + PAD, y + (ICON_ROW_HEIGHT - ICON_SLOT_SIZE) / 2, ICON_SLOT_SIZE, ICON_SLOT_SIZE);
		Row r = new Row(Kind.ICON, rect, back, slot, change, clear,
			null, null, null, null, null, null, null, null, null, null);
		rows.add(r);
		return y;
	}

	private static int swapRow(List<Row> rows, int x, int y, int w) {
		Rect rect = new Rect(x, y, w, BEHAVIOR_ROW_HEIGHT);
		int buttonW = Math.min(120, Math.max(80, w - 12));
		int by = y + (BEHAVIOR_ROW_HEIGHT - BUTTON_HEIGHT) / 2;
		Rect swap = new Rect(x + PAD, by, buttonW, BUTTON_HEIGHT);
		Row r = new Row(Kind.SWAP, rect, null, null, null, null,
			null, null, null, null, null, null, null, null, null, swap);
		rows.add(r);
		return y;
	}

	/**
	 * Right-anchored color row: swatch / hex / picker / reset hug the
	 * right edge and the label flexes to fill the remaining width. The hex value
	 * gets its own {@code hexBox} rect so render and tooltip hit-testing read one
	 * source instead of the Screen recomputing {@code hexX}.
	 */
	private static int colorRow(List<Row> rows, int x, int y, int w, Object target) {
		Rect rect = new Rect(x, y, w, COLOR_ROW_HEIGHT);
		int by = y + (COLOR_ROW_HEIGHT - BUTTON_HEIGHT) / 2;
		Rect reset = new Rect(x + w - PAD - WIDE_BUTTON_WIDTH, by, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT);
		Rect picker = new Rect(reset.x() - ROW_GAP - ICON_BUTTON_WIDTH, by, ICON_BUTTON_WIDTH, BUTTON_HEIGHT);
		int hexWidth = HEX_BOX_WIDTH;
		Rect hexBox = new Rect(picker.x() - ROW_GAP - hexWidth, by, hexWidth, BUTTON_HEIGHT);
		Rect swatch = new Rect(hexBox.x() - COLOR_SWATCH_GAP - SWATCH_SIZE,
			y + (COLOR_ROW_HEIGHT - SWATCH_SIZE) / 2, SWATCH_SIZE, SWATCH_SIZE);
		Row r = new Row(Kind.COLOR, rect, target, null, null, null,
			swatch, hexBox, picker, reset, null, null, null, null, null, null);
		rows.add(r);
		return y;
	}

	private static int priorityRow(List<Row> rows, int x, int y, int w) {
		Rect rect = new Rect(x, y, w, PRIORITY_ROW_HEIGHT);
		int by = y + (PRIORITY_ROW_HEIGHT - BUTTON_HEIGHT) / 2;
		Rect plus = new Rect(x + w - ICON_BUTTON_WIDTH - PAD, by, ICON_BUTTON_WIDTH, BUTTON_HEIGHT);
		Rect value = new Rect(plus.x() - ROW_GAP - PRIORITY_VALUE_WIDTH, by, PRIORITY_VALUE_WIDTH, BUTTON_HEIGHT);
		Rect minus = new Rect(value.x() - ROW_GAP - ICON_BUTTON_WIDTH, by, ICON_BUTTON_WIDTH, BUTTON_HEIGHT);
		Row r = new Row(Kind.PRIORITY, rect, null, null, null, null,
			null, null, null, null, minus, plus, value, null, null, null);
		rows.add(r);
		return y;
	}

	private static int idRow(List<Row> rows, int x, int y, int w) {
		Rect rect = new Rect(x, y, w, ID_ROW_HEIGHT);
		int by = y + (ID_ROW_HEIGHT - BUTTON_HEIGHT) / 2;
		Rect copy = new Rect(x + w - WIDE_BUTTON_WIDTH - PAD, by, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT);
		Row r = new Row(Kind.ID, rect, null, null, null, null,
			null, null, null, null, null, null, null, copy, null, null);
		rows.add(r);
		return y;
	}

	private static int enabledRow(List<Row> rows, int x, int y, int w) {
		Rect rect = new Rect(x, y, w, BEHAVIOR_ROW_HEIGHT);
		int sy = y + (BEHAVIOR_ROW_HEIGHT - BUTTON_HEIGHT) / 2;
		Rect sw = new Rect(x + w - SWITCH_WIDTH - PAD, sy, SWITCH_WIDTH, BUTTON_HEIGHT);
		Row r = new Row(Kind.ENABLED, rect, null, null, null, null,
			null, null, null, null, null, null, null, null, sw, null);
		rows.add(r);
		return y;
	}

	private static Row row(Kind kind, Rect rect, Object payload) {
		return new Row(kind, rect, payload, null, null, null,
			null, null, null, null, null, null, null, null, null, null);
	}
}
