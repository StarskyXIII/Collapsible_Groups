package com.starskyxiii.collapsible_groups.compat.jei.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreUiEditBoxStyleTest {
	@Test
	void hintAppliesExplicitPrimaryColorWithoutMutatingSource() {
		Component source = Component.literal("Search...");

		Component hint = OreUiEditBoxStyle.hint(source);

		assertEquals(Style.EMPTY, source.getStyle());
		assertNotEquals(Style.EMPTY, hint.getStyle());
		assertEquals(OreUiPalette.TEXT_PRIMARY & 0xFFFFFF, hint.getStyle().getColor().getValue());
		assertNull(source.getStyle().getColor());
	}

	@Test
	void hintPreservesExistingNonColorStyle() {
		Component source = Component.literal("Search...").withStyle(ChatFormatting.ITALIC);

		Component hint = OreUiEditBoxStyle.hint(source);

		assertEquals(OreUiPalette.TEXT_PRIMARY & 0xFFFFFF, hint.getStyle().getColor().getValue());
		assertNull(source.getStyle().getColor());
		assertTrue(source.getStyle().isItalic());
		assertTrue(hint.getStyle().isItalic());
	}
}
