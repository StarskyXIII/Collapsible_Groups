package com.starskyxiii.collapsible_groups.client.widget;

import java.util.Objects;

public final class CommandPress<T> {
    private T target;

    public void begin(T target) { this.target = target; }
    public void clear() { target = null; }
    public T target() { return target; }
    public boolean isHeld(T candidate) { return target != null && Objects.equals(target, candidate); }

    public T release(T candidate) {
        T previous = target;
        clear();
        return previous != null && Objects.equals(previous, candidate) ? previous : null;
    }
}
