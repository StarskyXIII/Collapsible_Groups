package com.starskyxiii.collapsible_groups.viewer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JeiSoftDependencyBootstrapTest {
	private static final String LOADER = TestLoader.class.getName();

	@BeforeEach
	void reset() {
		TestLoader.calls = 0;
	}

	@Test
	void invokesOnlyWhenJeiIsSelectedAndModIsLoaded() {
		var registrations = List.of(new JeiSoftDependencyBootstrap.Registration("mekanism", LOADER));
		JeiSoftDependencyBootstrap.registerSelected(true, id -> true, registrations);
		assertEquals(1, TestLoader.calls);
		JeiSoftDependencyBootstrap.registerSelected(false, id -> true, registrations);
		JeiSoftDependencyBootstrap.registerSelected(true, id -> false, registrations);
		assertEquals(1, TestLoader.calls);
	}

	@Test
	void falseGateDoesNotResolveMissingJeiBridge() {
		assertDoesNotThrow(() -> JeiSoftDependencyBootstrap.registerSelected(false, id -> true,
			List.of(new JeiSoftDependencyBootstrap.Registration("mekanism", "missing.jei.Loader"))));
	}

	public static final class TestLoader {
		private static int calls;
		public static void register() { calls++; }
	}
}
