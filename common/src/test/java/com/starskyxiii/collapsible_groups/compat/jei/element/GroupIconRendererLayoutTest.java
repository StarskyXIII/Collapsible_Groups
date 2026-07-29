package com.starskyxiii.collapsible_groups.compat.jei.element;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupIconRendererLayoutTest {
	@Test
	void emptyIconHasNoLayers() {
		assertTrue(GroupIconRenderer.layers(40, 24, 0).isEmpty());
	}

	@Test
	void singleLayerIsCenteredAndScaledAroundItsAbsoluteOrigin() {
		var layers = GroupIconRenderer.layers(40, 24, 1);

		assertEquals(1, layers.size());
		assertEquals(new GroupIconRenderer.Layer(0, 41, 25, 0, 0.9f), layers.getFirst());
	}

	@Test
	void doubleLayerKeepsBackToFrontOffsetsAndDepth() {
		var layers = GroupIconRenderer.layers(40, 24, 2);

		assertEquals(2, layers.size());
		assertEquals(new GroupIconRenderer.Layer(1, 42, 24, 0, 0.9f), layers.get(0));
		assertEquals(new GroupIconRenderer.Layer(0, 40, 26, 10, 0.9f), layers.get(1));
	}

	@Test
	void moreThanTwoIngredientsStillUsesOnlyTwoDisplayLayers() {
		assertEquals(GroupIconRenderer.layers(0, 0, 2), GroupIconRenderer.layers(0, 0, 5));
	}
}
