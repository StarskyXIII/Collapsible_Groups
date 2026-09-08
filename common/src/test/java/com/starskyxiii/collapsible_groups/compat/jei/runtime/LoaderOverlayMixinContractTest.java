package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderOverlayMixinContractTest {
	private static final String MIXIN_PATH =
		"src/main/java/com/starskyxiii/collapsible_groups/mixin/MixinIngredientListOverlay.java";

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "forge"})
	void loaderMixinUsesRequiredScreenRenderContract(String loader) throws IOException {
		Path root = Path.of(System.getProperty("collapsibleGroupsRoot"));
		String source = Files.readString(root.resolve(loader).resolve(MIXIN_PATH));
		assertEquals(2, occurrences(source, "method = \"drawScreen\""));
		assertTrue(source.contains("at = @At(\"HEAD\")"));
		assertTrue(source.contains("at = @At(\"TAIL\")"));
		assertTrue(source.contains("method = \"drawTooltips"));
		assertTrue(source.contains(
			"method = \"createInputHandler()Lmezz/jei/gui/input/IUserInputHandler;\""));
		assertFalse(source.contains("require = 0"));
	}

	private static int occurrences(String value, String needle) {
		return (value.length() - value.replace(needle, "").length()) / needle.length();
	}
}
