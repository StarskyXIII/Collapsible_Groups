package com.starskyxiii.collapsible_groups.client.manager;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManagerHeaderLayoutTest {
    @Test void narrowAndWideLayoutsKeepEveryControlInsideTheHeader() {
        for (int width : List.of(240, 320, 427, 640, 960)) {
            for (int sources : List.of(4, 5)) {
                for (int actions : List.of(0, 5)) {
                    var layout = ManagerHeaderLayout.create(width, sources, 104, actions);
                    var controls = new ArrayList<>(layout.sources());
                    controls.addAll(layout.batchActions());
                    controls.addAll(List.of(layout.search(), layout.sort(), layout.category()));
                    if (actions > 0) controls.add(layout.selectedCount());
                    for (var rect : controls) {
                        assertTrue(rect.x() >= 6 && rect.x() + rect.width() <= width - 6, rect.toString());
                        assertTrue(rect.width() > 0 && rect.y() + rect.height() < layout.height(), rect.toString());
                        assertTrue(rect.contains(rect.x() + rect.width() / 2.0, rect.y() + rect.height() / 2.0));
                    }
                    for (int i = 0; i < controls.size(); i++) {
                        for (int j = i + 1; j < controls.size(); j++) {
                            var a = controls.get(i);
                            var b = controls.get(j);
                            int overlapX = Math.min(a.x() + a.width(), b.x() + b.width()) - Math.max(a.x(), b.x());
                            int overlapY = Math.min(a.y() + a.height(), b.y() + b.height()) - Math.max(a.y(), b.y());
                            assertFalse(overlapX > 1 && overlapY > 0, a + " overlaps " + b);
                        }
                    }
                }
            }
        }
    }

    @Test void batchControlsWrapWithoutMovingTheSearchHitboxAwayFromItsVisualPosition() {
        var narrow = ManagerHeaderLayout.create(320, 5, 104, 5);
        var wide = ManagerHeaderLayout.create(960, 5, 104, 5);
        assertTrue(narrow.height() > wide.height());
        assertEquals(narrow.search().y(), narrow.sort().y());
        assertEquals(narrow.search().x() + narrow.search().width() + 4, narrow.sort().x());
        assertTrue(narrow.category().y() >= narrow.search().y());
    }
}
