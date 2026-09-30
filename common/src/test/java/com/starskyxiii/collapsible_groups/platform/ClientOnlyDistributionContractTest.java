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
	void forgeModFileIsSkippedOnDedicatedServers() throws IOException {
		String metadata = Files.readString(root().resolve("forge/src/main/resources/META-INF/mods.toml"));
		int clientOnlyFlag = metadata.indexOf("clientSideOnly = true");
		int firstModDeclaration = metadata.indexOf("[[mods]]");

		assertTrue(clientOnlyFlag >= 0, "Forge mod file must declare clientSideOnly = true");
		assertTrue(clientOnlyFlag < firstModDeclaration,
			"clientSideOnly must be a root mod-file property before [[mods]]");
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
