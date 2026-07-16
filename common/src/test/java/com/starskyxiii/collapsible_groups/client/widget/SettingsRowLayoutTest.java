package com.starskyxiii.collapsible_groups.client.widget;

import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.client.widget.SettingsRowLayout;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsRowLayoutTest {
	private static final Object[] FIVE_COLORS = {"a", "b", "c", "d", "e"};

	@Test
	void producesExpectedRowKinds() {
		SettingsRowLayout.Result result = SettingsRowLayout.compute(0, 0, 200, 0, FIVE_COLORS);

		long subheaders = result.rows().stream()
			.filter(r -> r.kind() == SettingsRowLayout.Kind.SUBHEADER).count();
		long icons = result.rows().stream()
			.filter(r -> r.kind() == SettingsRowLayout.Kind.ICON).count();
		long colors = result.rows().stream()
			.filter(r -> r.kind() == SettingsRowLayout.Kind.COLOR).count();
		assertEquals(3, subheaders, "three section headers (icons, colors, behavior)");
		assertEquals(2, icons, "front and back icon rows");
		assertEquals(5, colors, "five color rows");
		assertTrue(result.rows().stream().anyMatch(r -> r.kind() == SettingsRowLayout.Kind.SWAP));
		assertTrue(result.rows().stream().anyMatch(r -> r.kind() == SettingsRowLayout.Kind.PRIORITY));
		assertTrue(result.rows().stream().anyMatch(r -> r.kind() == SettingsRowLayout.Kind.ID));
		assertTrue(result.rows().stream().anyMatch(r -> r.kind() == SettingsRowLayout.Kind.ENABLED));
	}

	@Test
	void contentHeightExceedsBodyForDefaultLayout() {
		SettingsRowLayout.Result result = SettingsRowLayout.compute(0, 0, 200, 0, FIVE_COLORS);
		assertTrue(result.contentHeight() > 0);
		// Full section stack should not collapse to nothing; a realistic body of
		// ~230px must be smaller than the content it holds.
		assertTrue(result.contentHeight() > 230,
			"content should overflow a typical settings body, got " + result.contentHeight());
	}

	@Test
	void scrollOffsetShiftsEveryRectUniformly() {
		SettingsRowLayout.Result base = SettingsRowLayout.compute(10, 40, 200, 0, FIVE_COLORS);
		SettingsRowLayout.Result scrolled = SettingsRowLayout.compute(10, 40, 200, 25, FIVE_COLORS);

		assertEquals(base.rows().size(), scrolled.rows().size());
		assertEquals(base.contentHeight(), scrolled.contentHeight(),
			"scroll must not change content height");
		for (int i = 0; i < base.rows().size(); i++) {
			assertEquals(base.rows().get(i).rect().y() - 25, scrolled.rows().get(i).rect().y(),
				"row " + i + " y should shift by the scroll offset");
		}
	}

	@Test
	void allButtonChildRectsUseDesignHeight() {
		SettingsRowLayout.Result result = SettingsRowLayout.compute(0, 0, 200, 0, FIVE_COLORS);
		assertEquals(20, SettingsRowLayout.BUTTON_HEIGHT);
		assertEquals(UiSkinRenderer.BUTTON_DESIGN_HEIGHT, SettingsRowLayout.BUTTON_HEIGHT);

		for (SettingsRowLayout.Row row : result.rows()) {
			for (SettingsRowLayout.Rect child : buttonChildren(row)) {
				assertEquals(SettingsRowLayout.BUTTON_HEIGHT, child.height(),
					"button child in " + row.kind() + " must use the design height");
			}
		}
	}

	@Test
	void buttonDesignHeightIsTwenty() {
		assertEquals(20, UiSkinRenderer.BUTTON_DESIGN_HEIGHT);
	}

	@Test
	void childRectsStayWithinRowVertically() {
		SettingsRowLayout.Result result = SettingsRowLayout.compute(0, 0, 200, 0, FIVE_COLORS);
		for (SettingsRowLayout.Row row : result.rows()) {
			for (SettingsRowLayout.Rect child : buttonChildren(row)) {
				assertTrue(child.y() >= row.rect().y() && child.bottom() <= row.rect().bottom(),
					"child in " + row.kind() + " should fit inside its row");
			}
		}
	}

	@Test
	void colorControlsStayOrderedAndInsideRow() {
		SettingsRowLayout.Result result = SettingsRowLayout.compute(0, 0, 220, 0, FIVE_COLORS);
		for (SettingsRowLayout.Row row : result.rows()) {
			if (row.kind() != SettingsRowLayout.Kind.COLOR) continue;
			// Right-anchored flex layout (fix 3): swatch / hex / picker / reset hug
			// the right edge in order, reset stays inside the padded row, and the
			// label region (rect.x()+PAD .. swatch.x()) is what flexes.
			assertTrue(row.swatch().x() >= row.rect().x() + SettingsRowLayout.PAD,
				"swatch must start no earlier than the label pad");
			assertTrue(row.swatch().right() <= row.hexBox().x(), "swatch left of hex box");
			assertTrue(row.hexBox().right() <= row.pickerBtn().x(), "hex box left of picker");
			assertTrue(row.pickerBtn().right() <= row.resetBtn().x(), "picker left of reset");
			assertEquals(row.rect().right() - SettingsRowLayout.PAD, row.resetBtn().right(),
				"reset right edge hugs the padded row right edge");
		}
	}

	@Test
	void colorRowLabelRegionFlexesWithRowWidth() {
		SettingsRowLayout.Result narrow = SettingsRowLayout.compute(0, 0, 200, 0, FIVE_COLORS);
		SettingsRowLayout.Result wide = SettingsRowLayout.compute(0, 0, 320, 0, FIVE_COLORS);
		int narrowLabel = colorLabelWidth(narrow);
		int wideLabel = colorLabelWidth(wide);
		assertTrue(wideLabel > narrowLabel,
			"label region should grow with row width (got " + narrowLabel + " -> " + wideLabel + ")");
	}

	@Test
	void reservingScrollbarWidthKeepsControlsInsideNarrowerColumn() {
		// Fix 6+8c: the panel narrows the row column by the scrollbar width + gap.
		// Every right-anchored control must still land inside the narrower column
		// and the reset button must re-hug the new right edge.
		int full = 220;
		int reserved = full - 6 - 3; // SETTINGS_SCROLLBAR_WIDTH + SETTINGS_ROW_GAP
		SettingsRowLayout.Result result = SettingsRowLayout.compute(0, 0, reserved, 0, FIVE_COLORS);
		for (SettingsRowLayout.Row row : result.rows()) {
			if (row.kind() != SettingsRowLayout.Kind.COLOR) continue;
			assertEquals(reserved - SettingsRowLayout.PAD, row.resetBtn().right(),
				"reset hugs the narrowed column right edge");
			assertTrue(row.resetBtn().right() <= reserved, "controls stay inside the narrowed column");
		}
	}

	private static int colorLabelWidth(SettingsRowLayout.Result result) {
		for (SettingsRowLayout.Row row : result.rows()) {
			if (row.kind() == SettingsRowLayout.Kind.COLOR) {
				return row.swatch().x() - (row.rect().x() + SettingsRowLayout.PAD);
			}
		}
		throw new AssertionError("no color row");
	}

	private static Iterable<SettingsRowLayout.Rect> buttonChildren(SettingsRowLayout.Row row) {
		var out = new java.util.ArrayList<SettingsRowLayout.Rect>();
		addIfPresent(out, row.changeBtn());
		addIfPresent(out, row.clearBtn());
		addIfPresent(out, row.hexBox());
		addIfPresent(out, row.pickerBtn());
		addIfPresent(out, row.resetBtn());
		addIfPresent(out, row.stepMinus());
		addIfPresent(out, row.stepPlus());
		addIfPresent(out, row.valueBox());
		addIfPresent(out, row.copyBtn());
		addIfPresent(out, row.switchRect());
		addIfPresent(out, row.swapBtn());
		return out;
	}

	private static void addIfPresent(java.util.List<SettingsRowLayout.Rect> out, SettingsRowLayout.Rect rect) {
		if (rect != null) {
			out.add(rect);
		}
	}

	@Test
	void behaviorSectionOrdersEnabledPriorityId() {
		SettingsRowLayout.Result result = SettingsRowLayout.compute(0, 0, 200, 0, FIVE_COLORS);
		java.util.List<SettingsRowLayout.Kind> behavior = new java.util.ArrayList<>();
		for (SettingsRowLayout.Row row : result.rows()) {
			switch (row.kind()) {
				case ENABLED, PRIORITY, ID -> behavior.add(row.kind());
				default -> {
				}
			}
		}
		assertEquals(java.util.List.of(
				SettingsRowLayout.Kind.ENABLED,
				SettingsRowLayout.Kind.PRIORITY,
				SettingsRowLayout.Kind.ID),
			behavior, "behavior section must order Enabled -> Priority -> Group ID");
	}

	@Test
	void sectionBottomPadIsFoldedIntoContentHeight() {
		SettingsRowLayout.Result result = SettingsRowLayout.compute(0, 0, 200, 0, FIVE_COLORS);
		int lastBottom = 0;
		for (SettingsRowLayout.Row row : result.rows()) {
			lastBottom = Math.max(lastBottom, row.rect().bottom());
		}
		assertTrue(lastBottom < result.contentHeight(),
			"content height must extend past the last row bottom");
		assertTrue(result.contentHeight() - lastBottom >= SettingsRowLayout.SECTION_BOTTOM_PAD,
			"content height must reserve at least the section bottom pad below the last row");
	}

	@Test
	void subheaderHeightIsLockedAtTwenty() {
		assertEquals(20, SettingsRowLayout.SUBHEADER_HEIGHT);
	}

	@Test
	void everyEnumKindIsCovered() {
		SettingsRowLayout.Result result = SettingsRowLayout.compute(0, 0, 200, 0, FIVE_COLORS);
		EnumSet<SettingsRowLayout.Kind> seen = EnumSet.noneOf(SettingsRowLayout.Kind.class);
		for (SettingsRowLayout.Row row : result.rows()) {
			assertNotNull(row.rect());
			seen.add(row.kind());
		}
		assertEquals(EnumSet.allOf(SettingsRowLayout.Kind.class), seen);
	}
}
