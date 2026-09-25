package com.starskyxiii.collapsible_groups.client.widget;

import java.util.Objects;
import java.util.function.Predicate;

public final class SwitchHoverState<K> {
    private K suppressedKey;

    public void activated(K key) {
        suppressedKey = Objects.requireNonNull(key);
    }

    public void update(Predicate<K> hitTest) {
        if (suppressedKey != null && !hitTest.test(suppressedKey)) clear();
    }

    public boolean allowsHover(K key) {
        return key != null && !Objects.equals(key, suppressedKey);
    }

    public void clear() {
        suppressedKey = null;
    }
}
