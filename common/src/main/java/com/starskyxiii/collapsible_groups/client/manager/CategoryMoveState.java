package com.starskyxiii.collapsible_groups.client.manager;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CategoryMoveState {
    public enum Validation { READY, TARGETS_CHANGED, UNAVAILABLE }

    private List<String> targets;
    private List<CategoryChoices.Entry> entries = List.of();
    private List<CategoryChoices.Entry> visible = List.of();
    private String query = "";
    private String selected;

    public CategoryMoveState(Collection<String> targets) {
        this.targets = targets.stream().distinct().toList();
    }

    public List<String> targets() { return targets; }
    public List<CategoryChoices.Entry> visible() { return visible; }
    public String selected() { return selected; }
    public boolean canMove() { return selected != null && !targets.isEmpty(); }

    public void entries(List<CategoryChoices.Entry> entries) {
        this.entries = List.copyOf(entries);
        filter();
    }

    public void search(String query) {
        this.query = query.strip().toLowerCase(Locale.ROOT);
        filter();
    }

    private void filter() {
        visible = entries.stream().filter(entry -> entry.id().toLowerCase(Locale.ROOT).contains(query)
            || entry.label().getString().toLowerCase(Locale.ROOT).contains(query)).toList();
        if (visible.stream().noneMatch(entry -> entry.id().equals(selected))) selected = null;
    }

    public void select(String id) {
        selected = visible.stream().anyMatch(entry -> entry.id().equals(id)) ? id : null;
    }

    public Validation validate(Set<String> existing) {
        List<String> remaining = targets.stream().filter(existing::contains).toList();
        if (!remaining.equals(targets)) {
            targets = remaining;
            return Validation.TARGETS_CHANGED;
        }
        return canMove() ? Validation.READY : Validation.UNAVAILABLE;
    }
}
