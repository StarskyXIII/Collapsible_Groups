package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderIngredientListRendererMixinContractTest {
	private static final String PLUGIN_SOURCE =
		"src/main/java/com/starskyxiii/collapsible_groups/mixin/CollapsibleGroupsMixinPlugin.java";

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "forge"})
	void everyLoaderRegistersElementAndRenderingMixinsAsOptionalJeiIntegration(String loader) throws IOException {
		Path loaderRoot = root().resolve(loader);
		String mixinJson = Files.readString(loaderRoot.resolve("src/main/resources").resolve(
			"collapsible_groups." + loader + ".mixins.json"));
		String plugin = Files.readString(loaderRoot.resolve(PLUGIN_SOURCE));

		for (String mixin : new String[]{"MixinIngredientListRenderer", "MixinIngredientListSlot", "MixinIngredientElement"}) {
			assertTrue(mixinJson.contains("\"" + mixin + "\""), mixin);
			assertTrue(plugin.contains("\"com.starskyxiii.collapsible_groups.mixin." + mixin + "\""), mixin);
		}
	}

	private static Path root() {
		return Path.of(System.getProperty("collapsibleGroupsRoot"));
	}
}
