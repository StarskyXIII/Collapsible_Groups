package com.starskyxiii.collapsible_groups.viewer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ViewerCompatibilityEnvironmentTest {
	@Test void tmrvStubRoutesToEmiWithoutInspectingItsOldJeiVersion() {
		AtomicInteger checks = new AtomicInteger();
		ViewerCompatibilityEnvironment environment = ViewerCompatibilityEnvironment.detect(
			true, true, true, () -> {
				checks.incrementAndGet();
				return new ViewerCompatibilityEnvironment.JeiVersionCheck("19.27.0.343", false);
			});

		assertEquals(ViewerSelectionPolicy.Viewer.EMI, environment.selectedViewer());
		assertEquals(0, checks.get());
		assertFalse(environment.mayApplyJeiInternals());
		assertDoesNotThrow(environment::requireCompatibleSelectedViewer);
	}

	@Test void emiWithOldRealJeiAlsoSkipsJeiAndDisablesItsInternals() {
		ViewerCompatibilityEnvironment environment = ViewerCompatibilityEnvironment.detect(
			true, true, false, () -> fail("JEI version must not be inspected when EMI wins"));

		assertEquals(ViewerSelectionPolicy.Viewer.EMI, environment.selectedViewer());
		assertFalse(environment.mayApplyJeiInternals());
		assertDoesNotThrow(environment::requireCompatibleSelectedViewer);
	}

	@Test void oldSelectedJeiDisablesMixinsAndFailsBootstrapClearly() {
		ViewerCompatibilityEnvironment environment = ViewerCompatibilityEnvironment.detect(
			true, false, false,
			() -> new ViewerCompatibilityEnvironment.JeiVersionCheck("19.41.0.1", false));

		assertEquals(ViewerSelectionPolicy.Viewer.JEI, environment.selectedViewer());
		assertFalse(environment.mayApplyJeiInternals());
		IllegalStateException error = assertThrows(IllegalStateException.class,
			environment::requireCompatibleSelectedViewer);
		assertTrue(error.getMessage().contains("19.41.0.1"));
		assertTrue(error.getMessage().contains(ViewerCompatibilitySpec.minimumJeiVersion()));
	}

	@Test void supportedSelectedJeiEnablesInternals() {
		for (String version : new String[] {"19.42.0.379", "19.43.0.395"}) {
			ViewerCompatibilityEnvironment environment = ViewerCompatibilityEnvironment.detect(
				true, false, false,
				() -> new ViewerCompatibilityEnvironment.JeiVersionCheck(version, true));
			assertTrue(environment.mayApplyJeiInternals());
			assertDoesNotThrow(environment::requireCompatibleSelectedViewer);
		}
	}

	@Test void missingOrUnparseableSelectedJeiFailsClosed() {
		for (String detected : new String[] {"unknown", "not-a-version"}) {
			ViewerCompatibilityEnvironment environment = ViewerCompatibilityEnvironment.detect(
				true, false, false,
				() -> new ViewerCompatibilityEnvironment.JeiVersionCheck(detected, false));
			assertFalse(environment.mayApplyJeiInternals());
			assertThrows(IllegalStateException.class, environment::requireCompatibleSelectedViewer);
		}
	}

	@Test void noViewerAndPureEmiNeverInspectJei() {
		for (boolean emi : new boolean[] {false, true}) {
			ViewerCompatibilityEnvironment environment = ViewerCompatibilityEnvironment.detect(
				false, emi, false, () -> fail("JEI version must not be inspected"));
			assertDoesNotThrow(environment::requireCompatibleSelectedViewer);
		}
	}

	@Test void expandedRuntimeMinimumMatchesGradlePin() throws IOException {
		String expected = Files.readAllLines(root().resolve("gradle.properties")).stream()
			.filter(line -> line.startsWith("jei_version="))
			.findFirst().orElseThrow().substring("jei_version=".length());
		assertEquals(expected, ViewerCompatibilitySpec.minimumJeiVersion());
		assertFalse(ViewerCompatibilitySpec.minimumJeiVersion().contains("${"));
	}

	private static Path root() {
		return Path.of(System.getProperty("collapsibleGroupsRoot"));
	}
}
