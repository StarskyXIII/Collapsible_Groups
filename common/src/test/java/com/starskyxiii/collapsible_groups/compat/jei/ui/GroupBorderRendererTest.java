package com.starskyxiii.collapsible_groups.compat.jei.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupBorderRendererTest {
	@BeforeEach
	@AfterEach
	void clearPositions() {
		GroupBorderRenderer.clear();
	}

	@Test
	void beginFrameClearDropsPositionsFromIncompleteForegroundPass() {
		GroupBorderRenderer.registerPosition("incomplete", 10, 20);
		GroupBorderRenderer.registerPosition("incomplete", 28, 20);
		assertEquals(2, GroupBorderRenderer.currentFrameSize());

		GroupBorderRenderer.clear();

		assertEquals(0, GroupBorderRenderer.currentFrameSize());
	}
}
