package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderOverlayMixinContractTest {
	private static final String MIXIN_PATH =
		"src/main/java/com/starskyxiii/collapsible_groups/mixin/MixinIngredientListOverlay.java";

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "neoforge"})
	void activeLoaderMixinUsesCrossVersionOverlayContracts(String loader) throws IOException {
		Path root = Path.of(System.getProperty("collapsibleGroupsRoot"));
		String source = Files.readString(root.resolve(loader).resolve(MIXIN_PATH));

		Pattern constructorHook = Pattern.compile(
			"@Inject\\(\\s*" +
				"method = \\\"<init>\\\",\\s*" +
				"at = @At\\(\\\"TAIL\\\"\\),\\s*" +
				"require = 1,\\s*" +
				"allow = 1\\s*" +
				"\\)\\s*" +
				"private void cg\\$onInit\\(CallbackInfo ci\\)",
			Pattern.DOTALL
		);
		assertTrue(constructorHook.matcher(source).find());
		assertFalse(source.contains("method = \"<init>("));
		assertFalse(source.contains("IIngredientGridConfig"));
		assertTrue(source.contains("method = \"drawBackground("));
		assertTrue(source.contains("method = \"drawForeground("));
		assertTrue(source.contains("method = \"drawTooltips("));
		assertTrue(source.contains("method = \"createInputHandler()"));
		assertFalse(source.contains("method = \"drawScreen"));
		assertFalse(source.contains("require = 0"));
	}
}
