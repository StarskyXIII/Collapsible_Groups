package com.starskyxiii.collapsible_groups.client.manager;

import java.util.ArrayList;
import java.util.List;

public record ManagerHeaderLayout(Rect back, Rect primary, Rect secondary, List<Rect> sources,
                                  Rect search, Rect sort, int titleX, int titleWidth, int height) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }

    public int actionsY() { return primary.y(); }

    public int sourceAt(double x, double y) {
        for (int i = sources.size() - 1; i >= 0; i--) {
            if (sources.get(i).contains(x, y)) return i;
        }
        return -1;
    }

    public static ManagerHeaderLayout create(int width, List<Integer> sourceWidths, int backWidth,
                                             int primaryWidth, int secondaryWidth) {
        int usable = Math.max(1, width - 12);
        Rect back = new Rect(6, 5, Math.min(backWidth, usable), 20);
        int titleX = back.right() + 6;
        primaryWidth = Math.min(primaryWidth, usable);
        secondaryWidth = Math.min(secondaryWidth, usable);
        int total = primaryWidth + secondaryWidth + 6;
        int actionsY = width - 6 - total - titleX >= 80 ? 5 : 29;
        Rect secondary = new Rect(width - 6 - secondaryWidth, actionsY + (total > usable ? 24 : 0), secondaryWidth, 20);
        Rect primary = new Rect(total > usable ? width - 6 - primaryWidth : secondary.x() - 6 - primaryWidth,
            actionsY, primaryWidth, 20);
        int titleWidth = Math.max(0, (actionsY == 5 ? primary.x() - 6 : width - 6) - titleX);
        Row row = new Row(width, secondary.bottom() + 6);
        int sourceCount = sourceWidths.size();
        int preferredWidth = sourceWidths.stream().mapToInt(Integer::intValue).max().orElse(40);
        int sourceWidth = Math.max(1, Math.min(preferredWidth, (usable + Math.max(0, sourceCount - 1)) / Math.max(1, sourceCount)));
        List<Rect> sources = new ArrayList<>();
        for (int i = 0; i < sourceCount; i++) sources.add(row.add(sourceWidth, 18, i + 1 < sourceCount ? -1 : 8));
        if (row.remaining() < 120) row.next();
        int searchWidth = Math.max(1, Math.min(260, row.remaining() - 24));
        Rect search = new Rect(width - 6 - searchWidth - 24, row.y, searchWidth, 20);
        Rect sort = new Rect(search.right() + 4, row.y, 20, 20);
        return new ManagerHeaderLayout(back, primary, secondary, List.copyOf(sources), search, sort,
            titleX, titleWidth, Math.max(row.bottom(), sort.bottom()) + 5);
    }

    private static final class Row {
        private final int right;
        private int x = 6;
        private int y;
        private int rowHeight;

        private Row(int width, int y) { right = Math.max(7, width - 6); this.y = y; }
        private int remaining() { return right - x; }
        private void next() { x = 6; y += rowHeight + 4; rowHeight = 0; }

        private Rect add(int width, int height, int gap) {
            width = Math.min(width, right - 6);
            if (x > 6 && x + width > right) next();
            Rect result = new Rect(x, y, width, height);
            x += width + gap;
            rowHeight = Math.max(rowHeight, height);
            return result;
        }

        private int bottom() { return y + rowHeight; }
    }
}
