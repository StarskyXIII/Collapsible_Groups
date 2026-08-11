package com.starskyxiii.collapsible_groups.compat.emi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmiOverlayControllerLifecycleTest {
	@Test void staysHiddenUntilTheActiveEmiScreenIsReady() {
		assertHidden(EmiOverlayController.resolveState(false, true, true, false, true));
		assertHidden(EmiOverlayController.resolveState(true, false, true, false, true));
		assertHidden(EmiOverlayController.resolveState(true, true, false, false, true));
		assertHidden(EmiOverlayController.resolveState(true, true, true, true, true));
		assertHidden(EmiOverlayController.resolveState(true, true, true, false, false));
	}

	@Test void sameScreenReadyTransitionMakesTheButtonVisibleAndInteractive() {
		var loading = EmiOverlayController.resolveState(true, false, true, false, true);
		var ready = EmiOverlayController.resolveState(true, true, true, false, true);
		assertHidden(loading);
		assertTrue(ready.visible());
		assertTrue(ready.enabled());
	}

	@Test void reloadDisconnectAndDisableTransitionsClearStaleInputState() {
		assertHidden(EmiOverlayController.resolveState(true, false, true, false, true));
		assertHidden(EmiOverlayController.resolveState(true, true, false, false, true));
		assertHidden(EmiOverlayController.resolveState(true, true, true, true, true));
	}

	private static void assertHidden(EmiOverlayController.OverlayState state) {
		assertFalse(state.visible());
		assertFalse(state.enabled());
	}
}
