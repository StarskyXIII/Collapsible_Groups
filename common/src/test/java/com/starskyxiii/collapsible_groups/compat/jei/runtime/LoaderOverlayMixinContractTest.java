package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderOverlayMixinContractTest {
	private static final String MIXIN_PATH =
		"src/main/java/com/starskyxiii/collapsible_groups/mixin/MixinIngredientListOverlay.java";

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "forge", "neoforge"})
	void loaderMixinUsesRequiredSplitRenderContracts(String loader) throws IOException {
		Path root = Path.of(System.getProperty("collapsibleGroupsRoot"));
		String source = Files.readString(root.resolve(loader).resolve(MIXIN_PATH));
		if (loader.equals("fabric")) {
			assertTrue(source.contains("method = \"drawBackground\""));
			assertTrue(source.contains("method = \"drawForeground\""));
			assertTrue(source.contains("method = \"drawTooltips\""));
		} else {
			assertTrue(source.contains(
				"method = \"drawBackground(Lnet/minecraft/client/gui/GuiGraphics;)V\""));
			assertTrue(source.contains(
				"method = \"drawForeground(Lnet/minecraft/client/Minecraft;" +
					"Lnet/minecraft/client/gui/GuiGraphics;IIF)V\""));
			assertTrue(source.contains(
				"method = \"drawTooltips(Lnet/minecraft/client/Minecraft;" +
					"Lnet/minecraft/client/gui/GuiGraphics;II)V\""));
		}
		assertTrue(source.contains(
			"method = \"createInputHandler()Lmezz/jei/gui/input/IUserInputHandler;\""));
		assertFalse(source.contains("method = \"drawScreen"));
		assertFalse(source.contains("require = 0"));
	}
}
