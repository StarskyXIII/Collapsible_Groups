package com.starskyxiii.collapsible_groups.platform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientOnlyDistributionContractTest {
	private static final String NEOFORGE_ENTRYPOINT =
		"src/main/java/com/starskyxiii/collapsible_groups/CollapsibleGroups.java";

	@Test
	void fabricModIsRestrictedToTheClientEnvironment() throws IOException {
		JsonObject metadata = JsonParser.parseString(Files.readString(
			root().resolve("fabric/src/main/resources/fabric.mod.json"))).getAsJsonObject();

		assertEquals("client", metadata.get("environment").getAsString());
	}

	@Test
	void neoForgeEntrypointOnlyLoadsOnTheClient() throws IOException {
		String source = Files.readString(root().resolve("neoforge").resolve(NEOFORGE_ENTRYPOINT));

		assertTrue(source.contains("import net.neoforged.api.distmarker.Dist;"));
		assertTrue(source.contains("@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)"));
	}

	@Test
	void neoForgeKubeJsPluginIsMarkedClientOnly() throws IOException {
		String pluginEntry = Files.readString(
			root().resolve("neoforge/src/main/resources/kubejs.plugins.txt")).trim();

		assertEquals(
			"com.starskyxiii.collapsible_groups.compat.kubejs.CollapsibleGroupsKubeJSPlugin client",
			pluginEntry
		);
	}

	private static Path root() {
		return Path.of(System.getProperty("collapsibleGroupsRoot"));
	}
}
