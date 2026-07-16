package com.starskyxiii.collapsible_groups.platform;

import com.starskyxiii.collapsible_groups.platform.services.IPlatformHelper;

import java.nio.file.Path;

public final class TestPlatformHelper implements IPlatformHelper {
	public static final String CONFIG_DIR_PROPERTY = "collapsible_groups.test.config_dir";

	@Override
	public Path getConfigDir() {
		String configured = System.getProperty(CONFIG_DIR_PROPERTY);
		if (configured == null || configured.isBlank()) {
			return Path.of(System.getProperty("java.io.tmpdir"), "collapsible-groups-common-tests");
		}
		return Path.of(configured);
	}

	@Override
	public String getPlatformName() {
		return "Common Test";
	}

	@Override
	public boolean isModLoaded(String modId) {
		return false;
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return true;
	}
}
