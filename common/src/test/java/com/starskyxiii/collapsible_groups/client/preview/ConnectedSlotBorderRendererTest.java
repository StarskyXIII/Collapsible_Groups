package com.starskyxiii.collapsible_groups.client.preview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConnectedSlotBorderRendererTest {
	@Test
	void singleCellHasFourOuterSegments() {
		assertEquals(4, ConnectedSlotBorderRenderer.segments(List.of(new int[]{10, 10})).size());
	}

	@Test
	void horizontalAndVerticalNeighborsRemoveSharedEdges() {
		assertEquals(6, ConnectedSlotBorderRenderer.segments(List.of(new int[]{0, 0}, new int[]{18, 0})).size());
		assertEquals(6, ConnectedSlotBorderRenderer.segments(List.of(new int[]{0, 0}, new int[]{0, 18})).size());
	}

	@Test
	void lShapeHasNoInternalSharedEdges() {
		var segments = ConnectedSlotBorderRenderer.segments(
			List.of(new int[]{0, 0}, new int[]{18, 0}, new int[]{0, 18}));
		assertEquals(8, segments.size());
		assertFalse(segments.contains(new ConnectedSlotBorderRenderer.Segment(17, 0, 18, 16)));
		assertFalse(segments.contains(new ConnectedSlotBorderRenderer.Segment(0, 17, 16, 18)));
	}
}
