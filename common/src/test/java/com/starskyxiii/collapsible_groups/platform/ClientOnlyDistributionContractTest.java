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


	private static Path root() {
		return Path.of(System.getProperty("collapsibleGroupsRoot"));
	}
}
