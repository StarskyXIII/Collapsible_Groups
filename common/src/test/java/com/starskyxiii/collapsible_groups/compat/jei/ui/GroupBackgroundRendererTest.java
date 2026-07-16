package com.starskyxiii.collapsible_groups.compat.jei.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupBackgroundRendererTest {
	@BeforeEach
	@AfterEach
	void clearBuffers() {
		GroupBackgroundRenderer.clear();
	}

	@Test
	void registrationsAppearOnTheFollowingFrameOnly() {
		GroupBackgroundRenderer.registerHeader("header", 10, 20);

		assertTrue(GroupBackgroundRenderer.previousFrameForRender(true).isEmpty());

		GroupBackgroundRenderer.advanceFrame();
		assertEquals(
			List.of(new GroupBackgroundRenderer.BackgroundPosition(
				GroupBackgroundRenderer.Kind.HEADER, "header", 10, 20)),
			GroupBackgroundRenderer.previousFrameForRender(true)
		);
	}

	@Test
	void advancingAnEmptyFrameRemovesThePreviousFrame() {
		GroupBackgroundRenderer.registerChild("group", 4, 8);
		GroupBackgroundRenderer.advanceFrame();
		assertEquals(1, GroupBackgroundRenderer.previousFrameForRender(true).size());

		GroupBackgroundRenderer.advanceFrame();
		assertTrue(GroupBackgroundRenderer.previousFrameForRender(true).isEmpty());
	}

	@Test
	void clearDropsBothCurrentAndPreviousFrames() {
		GroupBackgroundRenderer.registerChild("current", 1, 2);
		GroupBackgroundRenderer.advanceFrame();
		GroupBackgroundRenderer.registerHeader("next", 3, 4);

		GroupBackgroundRenderer.clear();

		assertEquals(0, GroupBackgroundRenderer.currentFrameSize());
		assertTrue(GroupBackgroundRenderer.previousFrameForRender(true).isEmpty());
	}

	@Test
	void disabledConfigSkipsAndClearsPreviousButStillAllowsFrameAdvance() {
		GroupBackgroundRenderer.registerChild("old", 1, 2);
		GroupBackgroundRenderer.advanceFrame();
		assertTrue(GroupBackgroundRenderer.previousFrameForRender(false).isEmpty());

		GroupBackgroundRenderer.registerChild("current", 5, 6);
		GroupBackgroundRenderer.advanceFrame();

		assertEquals(
			List.of(new GroupBackgroundRenderer.BackgroundPosition(
				GroupBackgroundRenderer.Kind.CHILD, "current", 5, 6)),
			GroupBackgroundRenderer.previousFrameForRender(true)
		);
	}
}
