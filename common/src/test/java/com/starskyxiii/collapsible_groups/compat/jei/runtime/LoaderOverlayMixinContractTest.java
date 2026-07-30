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
	@ValueSource(strings = {"fabric", "neoforge"})
	void activeLoaderMixinUsesRequiredJei2920Contracts(String loader) throws IOException {
		Path root = Path.of(System.getProperty("collapsibleGroupsRoot"));
		String source = Files.readString(root.resolve(loader).resolve(MIXIN_PATH));

		assertTrue(source.contains(
			"method = \"<init>(Lmezz/jei/gui/overlay/ingredients/IIngredientGridSource;" +
				"Lmezz/jei/gui/filter/IFilterTextSource;\""));
		assertTrue(source.contains(
			"Lmezz/jei/api/runtime/IScreenHelper;" +
				"Lmezz/jei/gui/overlay/ingredients/IIngredientListOverlayContents;\""));
		assertTrue(source.contains(
			"Lmezz/jei/gui/overlay/bookmarks/history/LookupHistoryOverlay;\""));
		assertTrue(source.contains(
			"Lmezz/jei/common/config/IClientConfig;\""));
		assertTrue(source.contains(
			"Lmezz/jei/common/config/IClientToggleState;" +
				"Lmezz/jei/common/input/IInternalKeyMappings;)V\""));
		assertFalse(source.contains("IIngredientGridConfig"));
		assertTrue(source.contains("method = \"drawBackground("));
		assertTrue(source.contains("method = \"drawForeground("));
		assertTrue(source.contains("method = \"drawTooltips("));
		assertTrue(source.contains("method = \"createInputHandler()"));
		assertFalse(source.contains("method = \"drawScreen"));
		assertFalse(source.contains("require = 0"));
	}
}
