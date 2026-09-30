package com.starskyxiii.collapsible_groups.client.widget;

import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

class InputEventsTest {
    @Test void forwardedMouseRetainsEventModifiersAndDoubleClick() {
        int modifiers = GLFW.GLFW_MOD_SHIFT | InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER;
        var event = new MouseButtonEvent(12, 34, new MouseButtonInfo(1, modifiers));
        assertTrue(InputEvents.withMouse(event, true, () -> {
            var forwarded = InputEvents.mouse(event.x(), event.y(), event.button());
            assertEquals(event, forwarded);
            assertTrue(InputEvents.shiftDown());
            assertTrue(InputEvents.controlDown());
            assertTrue(InputEvents.doubleClick());
            return true;
        }));
        assertFalse(InputEvents.doubleClick());
    }

    @Test void nestedKeyboardDispatchRestoresMouseContextAfterFailure() {
        var mouse = new MouseButtonEvent(1, 2, new MouseButtonInfo(0, GLFW.GLFW_MOD_ALT));
        InputEvents.withMouse(mouse, true, () -> {
            assertThrows(IllegalStateException.class, () -> InputEvents.withModifiers(GLFW.GLFW_MOD_SHIFT, () -> {
                assertTrue(InputEvents.shiftDown());
                assertFalse(InputEvents.altDown());
                assertFalse(InputEvents.doubleClick());
                throw new IllegalStateException("test");
            }));
            assertTrue(InputEvents.altDown());
            assertFalse(InputEvents.shiftDown());
            assertTrue(InputEvents.doubleClick());
            return false;
        });
        assertFalse(InputEvents.doubleClick());
    }
}
