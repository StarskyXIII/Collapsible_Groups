package com.starskyxiii.collapsible_groups.client.widget;

import com.starskyxiii.collapsible_groups.client.widget.EditorChrome;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiSkinRendererSliderGeometryTest {
	@Test
	void knobFollowsValueAndOverhangsTrackEnds() {
		int x = 100;
		int y = 10;
		int width = 132;

		EditorChrome.Rect atMin = UiSkinRenderer.sliderKnobRect(x, y, width, 0, 255);
		assertEquals(x - UiSkinRenderer.SLIDER_KNOB_HALF, atMin.x());
		assertEquals(y, atMin.y());
		assertEquals(UiSkinRenderer.SLIDER_KNOB_WIDTH, atMin.width());
		assertEquals(UiSkinRenderer.SLIDER_KNOB_HEIGHT, atMin.height());

		EditorChrome.Rect atMax = UiSkinRenderer.sliderKnobRect(x, y, width, 255, 255);
		assertEquals(x + width - UiSkinRenderer.SLIDER_KNOB_HALF, atMax.x());

		EditorChrome.Rect mid = UiSkinRenderer.sliderKnobRect(x, y, width, 128, 255);
		int expectedCenter = x + width * 128 / 255;
		assertEquals(expectedCenter - UiSkinRenderer.SLIDER_KNOB_HALF, mid.x());
	}

	@Test
	void outOfRangeValuesClampToTrackEnds() {
		int x = 100;
		int y = 0;
		int width = 132;
		assertEquals(UiSkinRenderer.sliderKnobRect(x, y, width, 0, 255),
			UiSkinRenderer.sliderKnobRect(x, y, width, -50, 255));
		assertEquals(UiSkinRenderer.sliderKnobRect(x, y, width, 255, 255),
			UiSkinRenderer.sliderKnobRect(x, y, width, 400, 255));
	}

	@Test
	void hitBandCoversTrackPlusKnobOverhangExactly() {
		EditorChrome.Rect band = UiSkinRenderer.sliderHitBand(100, 10, 132);
		assertEquals(100 - UiSkinRenderer.SLIDER_KNOB_HALF, band.x());
		assertEquals(10, band.y());
		assertEquals(132 + UiSkinRenderer.SLIDER_KNOB_WIDTH, band.width());
		assertEquals(UiSkinRenderer.SLIDER_KNOB_HEIGHT, band.height());

		EditorChrome.Rect atMin = UiSkinRenderer.sliderKnobRect(100, 10, 132, 0, 255);
		EditorChrome.Rect atMax = UiSkinRenderer.sliderKnobRect(100, 10, 132, 255, 255);
		assertEquals(band.x(), atMin.x());
		assertEquals(band.right(), atMax.right());
	}
}
