package com.starskyxiii.collapsible_groups.group;

import java.util.List;

public record GroupEvaluation(long generation, Status status, int itemCount, int fluidCount, int genericCount,
    List<Issue> issues) {
    public enum Status { PENDING, COMPLETE, ERROR, UNAVAILABLE }
    public record Issue(Status status, String reason) {}

    public GroupEvaluation {
        issues = List.copyOf(issues);
        if (itemCount < 0 || fluidCount < 0 || genericCount < 0) throw new IllegalArgumentException("Negative match count");
    }

    public static GroupEvaluation pending() {
        return new GroupEvaluation(-1, Status.PENDING, 0, 0, 0, List.of());
    }

    public int count() { return itemCount + fluidCount + genericCount; }
    public boolean complete() { return status == Status.COMPLETE; }
    public boolean empty() { return complete() && count() == 0; }
    public boolean hasContent() { return complete() && count() > 0; }
}
