package com.starskyxiii.collapsible_groups.viewer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ViewerCompatibilityContractTest {
	@Test void fabricJeiSuggestionIsUnbounded() throws IOException {
		JsonObject metadata = JsonParser.parseString(Files.readString(
			root().resolve("fabric/src/main/resources/fabric.mod.json"))).getAsJsonObject();
		assertEquals("*", metadata.getAsJsonObject("suggests").get("jei").getAsString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"forge/src/main/resources/META-INF/mods.toml",
		"neoforge/src/main/resources/META-INF/neoforge.mods.toml"})
	void forgeMetadataKeepsJeiOptionalWithoutLoaderVersionGate(String relative) throws IOException {
		String metadata = Files.readString(root().resolve(relative));
		int start = metadata.indexOf("modId = \"jei\"");
		assertTrue(start >= 0);
		int end = metadata.indexOf("[[dependencies.", start + 1);
		String block = metadata.substring(start, end < 0 ? metadata.length() : end);
		assertTrue(block.contains("side = \"CLIENT\""));
		assertFalse(block.contains("versionRange"));
		assertTrue(block.contains(relative.startsWith("forge/") ? "mandatory = false" : "type = \"optional\""));
	}

	@ParameterizedTest
	@ValueSource(strings = {"fabric", "forge", "neoforge"})
	void everyLoaderGatesJeiMixinsAndBootstrapsBeforeOtherInitialization(String loader) throws IOException {
		Path loaderRoot = root().resolve(loader);
		String plugin = Files.readString(loaderRoot.resolve(
			"src/main/java/com/starskyxiii/collapsible_groups/mixin/CollapsibleGroupsMixinPlugin.java"));
		assertTrue(plugin.contains("environment.mayApplyJeiInternals()"));
		assertTrue(plugin.contains("environment.selectedViewer()"));
		assertFalse(plugin.contains("TooManyRecipeViewers"));

		String entrypointName = loader.equals("fabric") ? "CollapsibleGroupsFabric.java"
			: loader.equals("forge") ? "CollapsibleGroupsForge.java" : "CollapsibleGroups.java";
		String entrypoint = Files.readString(loaderRoot.resolve(
			"src/main/java/com/starskyxiii/collapsible_groups/" + entrypointName));
		int guard = entrypoint.indexOf("requireCompatibleSelectedViewer()");
		assertTrue(guard >= 0);
		int commonInit = entrypoint.indexOf("CommonClass.init()");
		if (commonInit >= 0) assertTrue(guard < commonInit);
		int config = entrypoint.indexOf("registerConfig(");
		if (config >= 0) assertTrue(guard < config);
	}

	@Test void compatibilityCoreDoesNotLinkViewerOrLoaderClasses() throws IOException {
		for (String file : new String[] {"ViewerSelectionPolicy.java", "ViewerCompatibilitySpec.java",
			"ViewerCompatibilityEnvironment.java"}) {
			String source = Files.readString(root().resolve(
				"common/src/main/java/com/starskyxiii/collapsible_groups/viewer/" + file));
			assertFalse(source.contains("mezz.jei"));
			assertFalse(source.contains("dev.emi"));
			assertFalse(source.contains("toomanyrecipeviewers."));
			assertFalse(source.contains("net.fabricmc"));
			assertFalse(source.contains("net.minecraftforge"));
			assertFalse(source.contains("net.neoforged"));
		}
	}

	private static Path root() {
		return Path.of(System.getProperty("collapsibleGroupsRoot"));
	}
}
