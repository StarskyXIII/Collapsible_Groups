package com.starskyxiii.collapsible_groups.client.manager.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PressedCardActionTest {
    private final GroupActionEligibility user = GroupActionEligibility.forSource(GroupSource.USER);

    @Test void releaseRequiresTheSameVisibleActionAndCurrentPermission() {
        var held = new PressedCardAction("group-a", GroupAction.EDIT, false);
        assertEquals(GroupAction.EDIT, held.release("group-a", GroupAction.EDIT, true, user, false));
        assertNull(held.release("group-b", GroupAction.EDIT, true, user, false));
        assertNull(held.release("group-a", GroupAction.DELETE, true, user, false));
        assertNull(held.release("group-a", null, true, user, false));
        assertNull(held.release("group-a", GroupAction.EDIT, false, user, false));
        assertNull(held.release("group-a", GroupAction.EDIT, true, GroupActionEligibility.forSource(GroupSource.BUILTIN), false));
    }

    @Test void shiftMustBeHeldAtBothEndsToSkipConfirmation() {
        for (boolean pressedShift : new boolean[] {false, true}) {
            for (boolean releasedShift : new boolean[] {false, true}) {
                var held = new PressedCardAction("group-a", GroupAction.DELETE, pressedShift);
                assertEquals(pressedShift && releasedShift ? GroupAction.SHIFT_DELETE : GroupAction.DELETE,
                    held.release("group-a", GroupAction.DELETE, true, user, releasedShift));
            }
        }
    }

    @Test void readOnlyGroupsCanOnlyReleaseTheirCopyAction() {
        var builtin = GroupActionEligibility.forSource(GroupSource.BUILTIN);
        var copy = new PressedCardAction("group-a", GroupAction.COPY_AS_CUSTOM, false);
        assertEquals(GroupAction.COPY_AS_CUSTOM, copy.release("group-a", GroupAction.COPY_AS_CUSTOM, true, builtin, false));
        assertNull(copy.release("group-a", GroupAction.COPY_AS_CUSTOM, true, user, false));
        assertNull(new PressedCardAction("group-a", GroupAction.DELETE, true).release("group-a", GroupAction.DELETE, true, builtin, true));
    }
}
