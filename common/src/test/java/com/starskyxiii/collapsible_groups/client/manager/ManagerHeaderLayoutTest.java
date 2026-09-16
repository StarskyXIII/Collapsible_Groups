package com.starskyxiii.collapsible_groups.client.manager;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManagerHeaderLayoutTest {
    @Test void narrowAndWideLayoutsKeepEveryControlInsideTheHeader() {
        for (int width : List.of(240, 320, 427, 640, 960)) {
            for (int sources : List.of(4, 5)) {
                for (int sourceWidth : List.of(40, 72, 104)) {
                    var layout = ManagerHeaderLayout.create(width, java.util.Collections.nCopies(sources, sourceWidth), 50, 92, 110);
                    var controls = new ArrayList<>(layout.sources());
                    controls.addAll(List.of(layout.search(), layout.sort(), layout.back(), layout.primary(), layout.secondary()));
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
                            boolean sharedSegmentEdge = j < sources && j == i + 1;
                            if (sharedSegmentEdge) {
                                assertEquals(1, overlapX);
                                assertEquals(18, overlapY);
                            } else assertFalse(overlapX > 0 && overlapY > 0, a + " overlaps " + b);
                        }
                    }
                }
            }
        }
    }

    @Test void wrappedActionsStayRightAlignedAndSearchStaysWithItsFilterButton() {
        var widths = List.of(40, 52, 44, 64, 52);
        var narrow = ManagerHeaderLayout.create(320, widths, 50, 92, 110);
        var wide = ManagerHeaderLayout.create(960, widths, 50, 92, 110);
        assertTrue(narrow.actionsY() >= 29);
        assertTrue(narrow.titleWidth() >= 120);
        assertEquals(5, wide.actionsY());
        assertTrue(narrow.height() > wide.height());
        assertEquals(narrow.search().y(), narrow.sort().y());
        assertEquals(narrow.search().x() + narrow.search().width() + 4, narrow.sort().x());
        assertEquals(314, narrow.secondary().right());
        assertEquals(narrow.secondary().x() - 6, narrow.primary().right());
        assertEquals(954, wide.secondary().right());
    }

    @Test void longActionLabelsWrapIndividuallyWithoutReversingTheirOrder() {
        var layout = ManagerHeaderLayout.create(240, List.of(40, 52, 64, 56, 48), 60, 150, 170);
        assertEquals(234, layout.primary().right());
        assertEquals(234, layout.secondary().right());
        assertTrue(layout.secondary().y() > layout.primary().y());
        assertTrue(layout.sources().get(0).y() >= layout.secondary().bottom());
    }

    @Test void unequalAndLongLabelsKeepOneEqualWidthSegmentGroup() {
        for (int width : List.of(240, 320, 640, 960)) {
            var layout = ManagerHeaderLayout.create(width, List.of(36, 140, 54, 180, 64), 50, 78, 104);
            var first = layout.sources().get(0);
            for (int i = 0; i < layout.sources().size(); i++) {
                var segment = layout.sources().get(i);
                assertEquals(first.width(), segment.width());
                assertEquals(first.y(), segment.y());
                assertEquals(18, segment.height());
                assertEquals(i, layout.sourceAt(segment.x(), segment.y() + 1));
                assertEquals(i, layout.sourceAt(segment.right() - 2, segment.y() + 1));
            }
            assertEquals(-1, layout.sourceAt(first.x(), first.bottom()));
            assertTrue(layout.sources().get(layout.sources().size() - 1).right() <= width - 6);
        }
    }

    @Test void rowWrappingHasNoOverlapAtEveryBoundaryWidth() {
        for (int width = 240; width <= 650; width++) {
            var layout = ManagerHeaderLayout.create(width, List.of(36, 50, 54, 80, 64), 50, 78, 104);
            assertEquals(width - 6, layout.sort().right());
            for (var source : layout.sources()) {
                if (source.y() == layout.search().y()) assertTrue(source.right() + 8 <= layout.search().x());
            }
        }
    }
}
