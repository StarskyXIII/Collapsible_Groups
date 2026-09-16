package com.starskyxiii.collapsible_groups.client.manager.model;

public record PressedCardAction(String groupId, GroupAction action, boolean shiftDown) {
    public boolean matches(String currentId, GroupAction target) {
        return groupId.equals(currentId) && action == target;
    }

    public GroupAction release(String currentId, GroupAction target, boolean interactive,
                               GroupActionEligibility eligibility, boolean currentShiftDown) {
        if (!interactive || !matches(currentId, target)) return null;
        GroupAction requested = action == GroupAction.DELETE && shiftDown && currentShiftDown
            ? GroupAction.SHIFT_DELETE : action;
        return eligibility.canRequest(requested) ? requested : null;
    }
}
