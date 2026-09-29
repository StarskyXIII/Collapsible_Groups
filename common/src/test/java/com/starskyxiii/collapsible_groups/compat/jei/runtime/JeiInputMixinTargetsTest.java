package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JeiInputMixinTargetsTest {
	@Test void unselectedJeiDoesNotInspectClasses() {
		assertNull(JeiInputMixinTargets.select(false, name -> { throw new AssertionError(name); }, "absent"));
	}

	@Test void existingLegacyOwnerIsSelected() {
		assertEquals(JeiInputMixinTargets.LEGACY,
			JeiInputMixinTargets.select(true, JeiInputMixinTargets.LEGACY::equals, "15.20.0.102"));
	}

	@Test void newOwnerTakesPriorityWhenBothClassesExist() {
		assertEquals(JeiInputMixinTargets.CURRENT, JeiInputMixinTargets.select(true, name -> true, "15.62.0.216"));
	}

	@Test void missingOwnersCannotSilentlyDisableGroupClicks() {
		IllegalStateException failure = assertThrows(IllegalStateException.class,
			() -> JeiInputMixinTargets.select(true, name -> false, "future"));
		assertTrue(failure.getMessage().contains("future"));
		assertTrue(failure.getMessage().contains(JeiInputMixinTargets.CURRENT));
		assertTrue(failure.getMessage().contains(JeiInputMixinTargets.LEGACY));
	}
}
