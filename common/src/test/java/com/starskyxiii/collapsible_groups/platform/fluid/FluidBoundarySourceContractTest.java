package com.starskyxiii.collapsible_groups.platform.fluid;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FluidBoundarySourceContractTest {
	@Test void fabricPlatformHasNoJeiLinkage() throws Exception {
		String platform = source("fabric", "platform/FabricPlatformHelper.java");

		assertFalse(platform.contains("mezz.jei"));
	}

	private static String source(String loader, String path) throws Exception {
		Path root = Path.of(System.getProperty("collapsibleGroupsRoot"));
		return Files.readString(root.resolve(loader).resolve("src/main/java/com/starskyxiii/collapsible_groups")
			.resolve(path));
	}
}
