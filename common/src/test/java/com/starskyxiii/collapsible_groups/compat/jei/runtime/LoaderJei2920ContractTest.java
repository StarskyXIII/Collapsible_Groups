package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderJei2920ContractTest {
	private static final String MIXIN_ROOT =
		"src/main/java/com/starskyxiii/collapsible_groups/mixin";

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "neoforge"})
	void filterMixinHasNoInvalidDirtyStateInvoker(String loader) throws IOException {
		String source = read(loader, "MixinIngredientFilter.java");
		assertFalse(source.contains("updateDirtyState"));
		assertFalse(source.contains("JeiIngredientFilterHook"));
		assertTrue(source.contains("this.cg$controller.getElements()"));
		assertFalse(source.contains("require = 0"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "neoforge"})
	void rendererMixinTargetsRelocatedJei2920Package(String loader) throws IOException {
		String source = read(loader, "MixinIngredientListRenderer.java");
		assertTrue(source.contains("overlay.ingredients.IngredientListRenderer"));
		assertTrue(source.contains("overlay.ingredients.IngredientListSlot"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "neoforge"})
	void pluginUsesOneJeiPresenceGateForAllInternalMixins(String loader) throws IOException {
		String source = read(loader, "CollapsibleGroupsMixinPlugin.java");
		assertTrue(source.contains("mezz/jei/api/JeiPlugin.class"));
		assertTrue(source.contains("MixinIngredientFilter"));
		assertTrue(source.contains("MixinBookmarkList"));
		assertTrue(source.contains("MixinIngredientListRenderer"));
		assertTrue(source.contains("MixinIngredientListOverlay"));
		assertTrue(source.contains("MixinGuiTextFieldFilterAccessor"));
		assertFalse(source.contains("isClassPresent(targetClassName)"));
	}

	private static String read(String loader, String file) throws IOException {
		Path root = Path.of(System.getProperty("collapsibleGroupsRoot"));
		return Files.readString(root.resolve(loader).resolve(MIXIN_ROOT).resolve(file));
	}
}
