package com.starskyxiii.collapsible_groups.viewer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Build-expanded compatibility constants used before viewer classes are safe to link. */
public final class ViewerCompatibilitySpec {
	private static final String RESOURCE = "/collapsible_groups-viewer-compat.properties";
	private static final String MINIMUM_JEI_VERSION = loadMinimumJeiVersion();

	private ViewerCompatibilitySpec() {}

	public static String minimumJeiVersion() {
		return MINIMUM_JEI_VERSION;
	}

	private static String loadMinimumJeiVersion() {
		Properties properties = new Properties();
		try (InputStream input = ViewerCompatibilitySpec.class.getResourceAsStream(RESOURCE)) {
			if (input == null) throw new IllegalStateException("Missing viewer compatibility resource " + RESOURCE);
			properties.load(input);
		} catch (IOException exception) {
			throw new IllegalStateException("Could not read viewer compatibility resource " + RESOURCE, exception);
		}
		String version = properties.getProperty("minimumJeiVersion", "").trim();
		if (version.isEmpty() || version.contains("${")) {
			throw new IllegalStateException("Invalid minimumJeiVersion in " + RESOURCE + ": " + version);
		}
		return version;
	}
}
