package com.starskyxiii.collapsible_groups.client.manager;

import com.starskyxiii.collapsible_groups.client.manager.ManagerHeaderLayout.Rect;

public record ManagerContentLayout(Rect content, Rect sidebar, Rect settings, Rect footerHint, boolean dockable) {
    public static final int SIDEBAR_WIDTH = 112;
    public static final int RAIL_WIDTH = 20;
    public static final int SIDEBAR_HEADER_HEIGHT = 24;
    public static final int CARD_WIDTH = 196;
    public static final int CARD_HEIGHT = 116;
    public static final int CARD_GAP = 6;
    public static final int FOOTER_HEIGHT = 28;

    public static ManagerContentLayout create(int width, int height, int headerHeight, boolean sidebarOpen, int settingsTextWidth) {
        boolean dockable = width >= SIDEBAR_WIDTH + 6 + CARD_WIDTH + 24;
        int left = (sidebarOpen && dockable ? SIDEBAR_WIDTH : RAIL_WIDTH) + 6;
        int top = Math.min(headerHeight, Math.max(0, height - FOOTER_HEIGHT));
        int bodyHeight = Math.max(0, height - FOOTER_HEIGHT - top);
        Rect content = new Rect(left, top, Math.max(0, width - left), bodyHeight);
        Rect sidebar = new Rect(0, top, dockable ? SIDEBAR_WIDTH : Math.max(0, Math.min(160, width - 24)), bodyHeight);
        int settingsWidth = Math.min(settingsTextWidth + 24, Math.max(0, width - 12));
        Rect settings = new Rect(width - 6 - settingsWidth, height - 24, settingsWidth, 20);
        Rect hint = new Rect(6, height - FOOTER_HEIGHT, Math.max(0, settings.x() - 12), FOOTER_HEIGHT);
        return new ManagerContentLayout(content, sidebar, settings, hint, dockable);
    }

    public Rect rail() { return new Rect(0, content.y(), RAIL_WIDTH, content.height()); }

    public Rect categoryToggle(boolean sidebarVisible) {
        Rect surface = sidebarVisible ? sidebar : rail();
        return new Rect(surface.right() - 19, surface.y() + 1, 18, Math.min(20, Math.max(0, surface.height() - 2)));
    }

    public int columns() { return Math.max(1, (content.width() - 18) / (CARD_WIDTH + CARD_GAP)); }
    public int scrollHeight() { return Math.max(0, content.height() - CARD_GAP); }
    public int maxScroll(int count) {
        int rows = (count + columns() - 1) / columns();
        return Math.max(0, rows * (CARD_HEIGHT + CARD_GAP) - scrollHeight());
    }
    public Rect card(int index, int scroll) {
        int columns = columns();
        int used = columns * CARD_WIDTH + (columns - 1) * CARD_GAP;
        int left = content.x() + Math.max(CARD_GAP, (content.width() - 12 - used) / 2);
        return new Rect(left + index % columns * (CARD_WIDTH + CARD_GAP),
            content.y() + CARD_GAP + index / columns * (CARD_HEIGHT + CARD_GAP) - scroll, CARD_WIDTH, CARD_HEIGHT);
    }
    public Rect scrollbar() { return new Rect(content.right() - 12, content.y() + CARD_GAP, 6, scrollHeight()); }
}
