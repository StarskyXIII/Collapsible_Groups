package com.starskyxiii.collapsible_groups.defaults;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronsApothicProviderContractTest {
	@Test
	void componentBasedProvidersAreNotPublishedByThe1201Slice() throws IOException {
		JsonObject language = JsonParser.parseString(Files.readString(root().resolve(
			"common/src/main/resources/assets/collapsible_groups/group_lang/en_us.json"))).getAsJsonObject();

		assertFalse(language.keySet().stream().anyMatch(
			key -> key.startsWith("collapsible_groups.group.__default_irons_apothic_gem_")));
		assertFalse(language.keySet().stream().anyMatch(
			key -> key.startsWith("collapsible_groups.group.__default_apotheosis_gem_")));
	}

	@Test
	void settingsExcludeNeoForge() throws IOException {
		String settings = Files.readString(root().resolve("settings.gradle"));
		assertFalse(settings.contains("include('neoforge')"));
	}

	@Test
	void settingsRetainBoth1201Loaders() throws IOException {
		String settings = Files.readString(root().resolve("settings.gradle"));
		assertTrue(settings.contains("include('forge')"));
		assertTrue(settings.contains("include('fabric')"));
	}

	@Test
	void generatedLanguageStillContainsCommonProviders() throws IOException {
		JsonObject language = JsonParser.parseString(Files.readString(root().resolve(
			"common/src/main/resources/assets/collapsible_groups/group_lang/en_us.json"))).getAsJsonObject();
		assertTrue(language.keySet().stream().anyMatch(key -> key.contains("__default_chipped_")));
	}

	@Test
	void groupLanguageGeneratorDoesNotScanNeoForgeSources() throws IOException {
		String build = Files.readString(root().resolve("build.gradle"));
		assertFalse(build.contains("fileTree('neoforge/src/main/java')"));
	}

	private static Path root() {
		return Path.of(System.getProperty("collapsibleGroupsRoot"));
	}
}
