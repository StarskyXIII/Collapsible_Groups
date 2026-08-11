package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderIngredientListRendererMixinContractTest {
	private static final String MIXIN_SOURCE =
		"src/main/java/com/starskyxiii/collapsible_groups/mixin/MixinIngredientListRenderer.java";
	private static final String PLUGIN_SOURCE =
		"src/main/java/com/starskyxiii/collapsible_groups/mixin/CollapsibleGroupsMixinPlugin.java";

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "forge", "neoforge"})
	void everyLoaderPreRendersCurrentJeiSlots(String loader) throws IOException {
		Path loaderRoot = root().resolve(loader);
		String source = Files.readString(loaderRoot.resolve(MIXIN_SOURCE));

		assertTrue(source.contains("@Mixin(value = IngredientListRenderer.class, remap = false)"));
		assertTrue(source.contains("private List<IngredientListSlot> slots;"));
		if (loader.equals("fabric")) {
			assertTrue(source.contains("method = \"render\""));
			assertFalse(source.contains(
				"method = \"render(Lnet/minecraft/client/gui/GuiGraphics;)V\""));
		} else {
			assertTrue(source.contains(
				"method = \"render(Lnet/minecraft/client/gui/GuiGraphics;)V\""));
		}
		assertTrue(source.contains("at = @At(\"HEAD\")"));
		assertTrue(source.contains("require = 1"));
		assertTrue(source.contains("slot.getOptionalElement()"));
		assertTrue(source.contains("slot.getRenderArea()"));
		assertTrue(source.contains("preRenderElement.drawPreRender"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "forge", "neoforge"})
	void everyLoaderRegistersRendererMixinAsOptionalJeiIntegration(String loader) throws IOException {
		Path loaderRoot = root().resolve(loader);
		String mixinJson = Files.readString(loaderRoot.resolve("src/main/resources").resolve(
			"collapsible_groups." + loader + ".mixins.json"));
		String plugin = Files.readString(loaderRoot.resolve(PLUGIN_SOURCE));

		assertTrue(mixinJson.contains("\"MixinIngredientListRenderer\""));
		assertTrue(plugin.contains(
			"\"com.starskyxiii.collapsible_groups.mixin.MixinIngredientListRenderer\""));
	}

	private static Path root() {
		return Path.of(System.getProperty("collapsibleGroupsRoot"));
	}
}
