package com.starskyxiii.collapsible_groups.group;

public record GroupLoadProblem(String groupId, GroupOrigin origin, String reason, boolean error) {}
