package com.starskyxiii.collapsible_groups.client.widget;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandPressTest {
    @Test void releaseRequiresTheSameEnabledTargetAndExecutesOnlyOnce() {
        var press = new CommandPress<String>();
        press.begin("rename:one");
        assertTrue(press.isHeld("rename:one"));
        assertFalse(press.isHeld("rename:two"));
        assertEquals("rename:one", press.release("rename:one"));
        assertNull(press.release("rename:one"));
        press.begin("rename:one");
        assertNull(press.release("rename:two"));
        press.begin("rename:one");
        assertNull(press.release(null));
    }

    @Test void closingOrRebuildingTheSurfaceCancelsTheOldCommand() {
        var press = new CommandPress<String>();
        press.begin("save");
        press.clear();
        assertNull(press.release("save"));
        press.begin(null);
        assertFalse(press.isHeld(null));
    }
}
