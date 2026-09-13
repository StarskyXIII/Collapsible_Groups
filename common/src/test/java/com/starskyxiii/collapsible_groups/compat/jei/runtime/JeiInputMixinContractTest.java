package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiInputMixinContractTest {
	private static final String MIXIN_SOURCE = "src/main/java/com/starskyxiii/collapsible_groups/mixin/";

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "forge", "neoforge"})
	void clickAndBookmarkHooksAcceptBothInputDescriptorsExactlyOnce(String loader) throws IOException {
		String click = Files.readString(root().resolve(loader).resolve(MIXIN_SOURCE + "MixinJeiElement.java"));
		String bookmark = Files.readString(root().resolve(loader).resolve(MIXIN_SOURCE + "MixinBookmarkList.java"));
		for (String inputPackage : List.of("gui", "common")) {
			assertTrue(click.contains("handleClick(Lmezz/jei/" + inputPackage
				+ "/input/UserInput;Lmezz/jei/common/input/IInternalKeyMappings;)Z"));
			assertTrue(bookmark.contains("onElementBookmarked(Lmezz/jei/gui/overlay/elements/IElement;Lmezz/jei/"
				+ inputPackage + "/input/UserInput;Lmezz/jei/gui/overlay/bookmarks/BookmarkOverlay;)Z"));
		}
		for (String source : List.of(click, bookmark)) {
			assertTrue(source.contains("method = {"));
			assertTrue(source.contains("require = 1"));
			assertTrue(source.contains("allow = 1"));
			assertTrue(source.contains("@Coerce IJeiUserInput input"));
			assertFalse(source.contains("require = 0"));
			assertFalse(source.contains("import mezz.jei.gui.input.UserInput;"));
		}
		assertTrue(click.contains("public interface MixinJeiElement"));
		assertTrue(click.contains("instanceof JeiClickableElement"));
		assertTrue(click.contains("element.handleJeiClick(input, keyBindings)"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "forge", "neoforge"})
	void interfaceMixinUsesTheSelectedJeiGateAndSupportedRuntime(String loader) throws IOException {
		Path loaderRoot = root().resolve(loader);
		var config = JsonParser.parseString(Files.readString(loaderRoot.resolve("src/main/resources/"
			+ "collapsible_groups." + loader + ".mixins.json"))).getAsJsonObject();
		assertEquals("0.8.7", config.get("minVersion").getAsString());
		assertEquals("JAVA_21", config.get("compatibilityLevel").getAsString());
		assertTrue(config.getAsJsonArray("client").asList().stream()
			.anyMatch(value -> value.getAsString().equals("MixinJeiElement")));
		String plugin = Files.readString(loaderRoot.resolve(MIXIN_SOURCE + "CollapsibleGroupsMixinPlugin.java"));
		String jeiMixins = plugin.substring(plugin.indexOf("JEI_INTERNAL_MIXINS = Set.of("),
			plugin.indexOf("\n\t);", plugin.indexOf("JEI_INTERNAL_MIXINS = Set.of(")));
		assertTrue(jeiMixins.contains("com.starskyxiii.collapsible_groups.mixin.MixinJeiElement"));
		assertTrue(plugin.contains("environment.mayApplyJeiInternals()"));
	}

	@Test
	void inputMixinShellsRemainIdenticalAcrossLoaders() throws IOException {
		for (String name : List.of("MixinJeiElement.java", "MixinBookmarkList.java")) {
			byte[] fabric = Files.readAllBytes(root().resolve("fabric").resolve(MIXIN_SOURCE + name));
			for (String loader : List.of("forge", "neoforge")) {
				assertArrayEquals(fabric, Files.readAllBytes(root().resolve(loader).resolve(MIXIN_SOURCE + name)));
			}
		}
	}

	private static Path root() {
		return Path.of(System.getProperty("collapsibleGroupsRoot"));
	}
}
