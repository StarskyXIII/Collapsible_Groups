package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiVersionMetadataContractTest {
	@Test
	void activeLoadersRequireMinimumJeiWithoutUpperBound() throws IOException {
		Path root = Path.of(System.getProperty("collapsibleGroupsRoot"));
		String fabric = Files.readString(root.resolve("fabric/src/main/resources/fabric.mod.json"));
		String neoforge = Files.readString(
			root.resolve("neoforge/src/main/resources/META-INF/neoforge.mods.toml"));
		String properties = Files.readString(root.resolve("gradle.properties"));

		assertTrue(properties.contains("jei_version=29.20.0.60"));
		assertTrue(fabric.contains("\"jei\": \">=${jei_version}\""));
		assertTrue(fabric.contains("\"<${jei_version}\""));
		assertFalse(fabric.contains("<29.21"));
		assertFalse(fabric.contains(">=29.21"));
		assertTrue(neoforge.contains("versionRange = \"[${jei_version},)\""));
		assertFalse(neoforge.contains("[${jei_version},29.21)"));
	}
}
