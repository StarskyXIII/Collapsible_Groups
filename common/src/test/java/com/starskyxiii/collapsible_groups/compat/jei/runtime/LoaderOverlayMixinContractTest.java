package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderOverlayMixinContractTest {
	private static final String MIXIN_PATH =
		"src/main/java/com/starskyxiii/collapsible_groups/mixin/MixinIngredientListOverlay.java";

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "neoforge"})
	void loaderMixinUsesJei29ExtractorRenderContract(String loader) throws IOException {
		Path root = Path.of(System.getProperty("collapsibleGroupsRoot"));
		String source = Files.readString(root.resolve(loader).resolve(MIXIN_PATH));
		assertTrue(source.contains("method = \"drawScreen\""));
		assertTrue(source.contains("method = \"drawTooltips\""));
		assertTrue(source.contains("method = \"createInputHandler()"));
		assertTrue(source.contains("GuiGraphicsExtractor"));
		assertTrue(source.contains("require = 0"));
	}
}
