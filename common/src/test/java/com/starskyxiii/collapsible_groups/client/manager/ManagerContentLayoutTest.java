package com.starskyxiii.collapsible_groups.client.manager;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ManagerContentLayoutTest {
    @Test void sidebarOnlyDocksWhenAWholeCardAndScrollbarFit() {
        var narrow = ManagerContentLayout.create(337, 240, 90, true, true);
        var wide = ManagerContentLayout.create(338, 240, 90, true, true);
        assertFalse(narrow.dockable());
        assertEquals(26, narrow.content().x());
        assertTrue(wide.dockable());
        assertTrue(wide.card(0, 0).right() < wide.scrollbar().x());
        assertFalse(wide.content().contains(wide.sidebar().x() + 20, wide.sidebar().y() + 20));
    }

    @Test void categoryToggleStaysInTheBodyAndOutsideDockedCards() {
        for (int width : List.of(240, 320, 640)) {
            for (boolean open : List.of(false, true)) {
                var layout = ManagerContentLayout.create(width, 240, 90, open, true);
                var toggle = layout.categoryToggle(open);
                assertEquals(18, toggle.width());
                assertEquals(20, toggle.height());
                assertTrue(toggle.y() >= 90);
                assertTrue(toggle.bottom() <= 90 + ManagerContentLayout.SIDEBAR_HEADER_HEIGHT);
                if (!open || layout.dockable()) assertTrue(toggle.right() <= layout.content().x());
            }
        }
    }

    @Test void narrowDrawerDoesNotReflowTheUnderlyingCards() {
        var open = ManagerContentLayout.create(320, 240, 95, true, true);
        var closed = ManagerContentLayout.create(320, 240, 95, false, true);
        assertEquals(closed.content(), open.content());
        assertEquals(closed.card(1, 30), open.card(1, 30));
    }

    @Test void footerControlsAndContentCannotReceiveEachOthersClicks() {
        for (int width : List.of(240, 320, 640)) {
            var layout = ManagerContentLayout.create(width, 240, 95, true, true);
            assertTrue(layout.footerHint().right() < layout.settings().x());
            assertFalse(layout.footerHint().contains(layout.settings().x() + 1, layout.settings().y() + 1));
            assertFalse(layout.content().contains(layout.settings().x() + 1, layout.settings().y() + 1));
            assertFalse(layout.content().contains(layout.content().x() + 1, 94));
        }
    }

    @Test void lastCardControlsRemainReachableEvenWithAWarningBand() {
        for (int width : List.of(320, 427, 640)) {
            for (boolean open : List.of(false, true)) {
                var header = ManagerHeaderLayout.create(width, List.of(40, 52, 64, 56, 48), 50, 92, 110);
                var layout = ManagerContentLayout.create(width, 240, header.height() + 16, open, true);
                assertTrue(layout.content().height() > 20);
                var last = layout.card(38, layout.maxScroll(39));
                assertTrue(last.bottom() <= layout.content().bottom());
                assertTrue(last.bottom() - 8 >= layout.content().y());
            }
        }
    }
    @Test void disablingSidebarReclaimsRailAndRemovesToggleHitArea() {
        var layout = ManagerContentLayout.create(320, 240, 90, true, false);
        assertEquals(0, layout.content().x());
        assertEquals(0, layout.rail().width());
        assertFalse(layout.categoryToggle(true).contains(0, 90));
    }
}
