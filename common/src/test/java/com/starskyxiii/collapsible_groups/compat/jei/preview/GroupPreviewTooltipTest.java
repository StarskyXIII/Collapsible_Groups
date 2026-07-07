package com.starskyxiii.collapsible_groups.compat.jei.preview;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupPreviewTooltipTest {

	@Test
	void nameLineUsesGivenNameColor() {
		GroupPreviewTooltip.Result result = GroupPreviewTooltip.build(
			"My Group", 0x112233, 3, 0, 0, false, List.of());

		Component name = result.lines().get(0);
		TextColor color = name.getStyle().getColor();
		assertNotNull(color);
		assertEquals(0x112233, color.getValue());
		assertEquals("My Group", name.getString());
	}

	@Test
	void collapsedIncludesPreviewVisualWithExpandHint() {
		GroupPreviewTooltip.Result result = GroupPreviewTooltip.build(
			"G", 0xFFFFFF, 1, 0, 0, false, List.of(GroupPreviewEntry.ofFluid(new Object())));

		// name + count + action hint.
		assertEquals(3, result.lines().size());
		assertTrue(result.visual().isPresent(), "collapsed header shows the preview grid");
		assertTrue(result.visual().orElseThrow() instanceof PreviewTooltipComponent);
	}

	@Test
	void expandedOmitsPreviewVisual() {
		GroupPreviewTooltip.Result result = GroupPreviewTooltip.build(
			"G", 0xFFFFFF, 1, 2, 3, true, List.of());

		assertFalse(result.visual().isPresent(), "expanded header has no preview grid");
	}

	@Test
	void countLabelEmptyWhenAllZero() {
		Component label = GroupPreviewTooltip.buildCountLabel(0, 0, 0);
		assertEquals("", label.getString());
	}
}
