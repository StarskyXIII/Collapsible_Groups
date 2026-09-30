package com.starskyxiii.collapsible_groups.client.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwitchHoverStateTest {
    @Test void repeatedActivationsStayStableUntilPointerLeavesAndReturns() {
        var state = new SwitchHoverState<String>();
        assertTrue(state.allowsHover("toggle"));
        state.activated("toggle");
        state.update("toggle"::equals);
        assertFalse(state.allowsHover("toggle"));
        state.activated("toggle");
        state.update("toggle"::equals);
        assertFalse(state.allowsHover("toggle"));
        state.update(key -> false);
        state.update("toggle"::equals);
        assertTrue(state.allowsHover("toggle"));
    }

    @Test void anotherControlIsIndependentAndReplacesThePreviousSession() {
        var state = new SwitchHoverState<String>();
        state.activated("first");
        assertTrue(state.allowsHover("second"));
        state.update("second"::equals);
        state.activated("second");
        assertTrue(state.allowsHover("first"));
        assertFalse(state.allowsHover("second"));
    }

    @Test void recreatedTargetWithTheSameKeyKeepsItsSession() {
        var state = new SwitchHoverState<String>();
        state.activated(new String("group"));
        state.update(new String("group")::equals);
        assertFalse(state.allowsHover(new String("group")));
    }

    @Test void targetRemovalAndScreenEndReleaseTheSession() {
        var state = new SwitchHoverState<String>();
        state.activated("group");
        state.update(key -> false);
        assertTrue(state.allowsHover("group"));
        state.activated("group");
        state.clear();
        assertTrue(state.allowsHover("group"));
    }

    @Test void idleSessionDoesNotPerformHitTests() {
        var state = new SwitchHoverState<String>();
        state.update(key -> fail("No target to hit-test"));
        assertFalse(state.allowsHover(null));
    }
}
