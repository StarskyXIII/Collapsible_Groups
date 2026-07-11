package com.starskyxiii.collapsible_groups.compat.jei.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OreUiRendererSliderGeometryTest {
	@Test
	void knobFollowsValueAndOverhangsTrackEnds() {
		int x = 100;
		int y = 10;
		int width = 132;

		EditorChrome.Rect atMin = OreUiRenderer.sliderKnobRect(x, y, width, 0, 255);
		assertEquals(x - OreUiRenderer.SLIDER_KNOB_HALF, atMin.x());
		assertEquals(y, atMin.y());
		assertEquals(OreUiRenderer.SLIDER_KNOB_WIDTH, atMin.width());
		assertEquals(OreUiRenderer.SLIDER_KNOB_HEIGHT, atMin.height());

		EditorChrome.Rect atMax = OreUiRenderer.sliderKnobRect(x, y, width, 255, 255);
		assertEquals(x + width - OreUiRenderer.SLIDER_KNOB_HALF, atMax.x());

		EditorChrome.Rect mid = OreUiRenderer.sliderKnobRect(x, y, width, 128, 255);
		int expectedCenter = x + width * 128 / 255;
		assertEquals(expectedCenter - OreUiRenderer.SLIDER_KNOB_HALF, mid.x());
	}

	@Test
	void outOfRangeValuesClampToTrackEnds() {
		int x = 100;
		int y = 0;
		int width = 132;
		assertEquals(OreUiRenderer.sliderKnobRect(x, y, width, 0, 255),
			OreUiRenderer.sliderKnobRect(x, y, width, -50, 255));
		assertEquals(OreUiRenderer.sliderKnobRect(x, y, width, 255, 255),
			OreUiRenderer.sliderKnobRect(x, y, width, 400, 255));
	}

	@Test
	void hitBandCoversTrackPlusKnobOverhangExactly() {
		EditorChrome.Rect band = OreUiRenderer.sliderHitBand(100, 10, 132);
		assertEquals(100 - OreUiRenderer.SLIDER_KNOB_HALF, band.x());
		assertEquals(10, band.y());
		assertEquals(132 + OreUiRenderer.SLIDER_KNOB_WIDTH, band.width());
		assertEquals(OreUiRenderer.SLIDER_KNOB_HEIGHT, band.height());

		EditorChrome.Rect atMin = OreUiRenderer.sliderKnobRect(100, 10, 132, 0, 255);
		EditorChrome.Rect atMax = OreUiRenderer.sliderKnobRect(100, 10, 132, 255, 255);
		assertEquals(band.x(), atMin.x());
		assertEquals(band.right(), atMax.right());
	}
}
