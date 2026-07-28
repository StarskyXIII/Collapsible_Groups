package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import mezz.jei.gui.overlay.IngredientListOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class JeiOverlayRenderContractTest {
	@Test
	void jei29OverlayRetainsExtractorDrawScreenEntryPoint() throws ReflectiveOperationException {
		assertNotNull(IngredientListOverlay.class.getDeclaredMethod(
			"drawScreen",
			Minecraft.class,
			GuiGraphicsExtractor.class,
			int.class,
			int.class,
			float.class
		));
	}
}
