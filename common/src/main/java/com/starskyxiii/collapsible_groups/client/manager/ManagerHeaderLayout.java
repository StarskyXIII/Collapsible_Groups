package com.starskyxiii.collapsible_groups.client.manager;

import java.util.ArrayList;
import java.util.List;

public record ManagerHeaderLayout(List<Rect> sources, List<Rect> batchActions, Rect search, Rect sort,
                                  Rect category, Rect selectedCount, int actionsY, int height, int titleWidth) {
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public static ManagerHeaderLayout create(int width, int sourceCount, int sourceWidth, int batchCount) {
        int usable = Math.max(1, width - 12);
        int actionsY = width - 306 >= 120 ? 5 : 29;
        var flow = new Row(width, actionsY + 26);
        int segmentWidth = Math.min(sourceWidth, Math.max(40, (usable + sourceCount - 1) / Math.max(1, sourceCount)));
        List<Rect> sources = new ArrayList<>();
        for (int i = 0; i < sourceCount; i++) sources.add(flow.add(segmentWidth, 18, -1));
        flow.x += 8;
        List<Rect> actions = new ArrayList<>();
        int actionWidth = Math.min(72, Math.max(40, (usable - 6 * Math.max(0, batchCount - 1)) / Math.max(1, batchCount)));
        for (int i = 0; i < batchCount; i++) actions.add(flow.add(actionWidth, 20, 6));
        var controls = new Row(width, flow.bottom() + 4);
        int searchWidth = Math.min(180, Math.max(96, usable - 24 - 140 - 8));
        Rect searchAndSort = controls.add(Math.min(usable, searchWidth + 24), 20, 8);
        Rect search = new Rect(searchAndSort.x(), searchAndSort.y(), searchAndSort.width() - 24, 18);
        Rect sort = new Rect(search.x() + search.width() + 4, search.y(), 20, 20);
        Rect category = controls.add(Math.min(140, usable), 20, 8);
        Rect selected = batchCount == 0 ? new Rect(0, 0, 0, 0)
            : controls.add(Math.min(174, Math.max(90, usable - 148)), 20, 8);
        int titleWidth = actionsY == 5 ? Math.max(0, width - 306) : Math.max(0, width - 68);
        return new ManagerHeaderLayout(List.copyOf(sources), List.copyOf(actions), search, sort, category, selected,
            actionsY, controls.bottom() + 5, titleWidth);
    }

    private static final class Row {
        private final int right;
        private int x = 6;
        private int y;
        private int rowHeight;

        private Row(int width, int y) { right = Math.max(7, width - 6); this.y = y; }

        private Rect add(int width, int height, int gap) {
            width = Math.min(width, right - 6);
            if (x > 6 && x + width > right) { x = 6; y += rowHeight + 4; rowHeight = 0; }
            Rect result = new Rect(x, y, width, height);
            x += width + gap;
            rowHeight = Math.max(rowHeight, height);
            return result;
        }

        private int bottom() { return y + rowHeight; }
    }
}
