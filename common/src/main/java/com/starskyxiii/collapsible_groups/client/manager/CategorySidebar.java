package com.starskyxiii.collapsible_groups.client.manager;

import com.starskyxiii.collapsible_groups.client.manager.ManagerHeaderLayout.Rect;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class CategorySidebar {
    private static final int ROW_HEIGHT = 20;
    private List<CategoryChoices.Entry> entries = List.of();
    private Rect bounds = new Rect(0, 0, 0, 0);
    private String selectedId = CategoryChoices.ALL;
    private int first;
    private int focused;
    private int held = -1;
    private boolean dragging;
    private double thumbGrab;

    public void layout(Rect bounds) {
        boolean reveal = visibleRows() == 0;
        cancelPress();
        this.bounds = bounds;
        clampFirst();
        if (reveal) revealFocused();
    }

    public void entries(List<CategoryChoices.Entry> entries, String selectedId) {
        boolean changed = !this.selectedId.equals(selectedId) || !this.entries.equals(entries);
        this.entries = List.copyOf(entries);
        this.selectedId = selectedId;
        if (changed) {
            cancelPress();
            focused = 0;
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(selectedId)) focused = i;
            revealFocused();
        }
        clampFirst();
    }

    public Component render(GuiGraphics g, Font font, int mouseX, int mouseY, boolean keyboardFocus) {
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), UiPalette.SURFACE_DARK);
        g.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), UiPalette.DIVIDER);
        Component tooltip = null;
        String heading = Component.translatable("collapsible_groups.manager.categories").getString();
        g.drawString(font, ellipsize(font, heading, bounds.width() - 30), bounds.x() + 6,
            UiSkinRenderer.centeredTextY(font, bounds.y(), ManagerContentLayout.SIDEBAR_HEADER_HEIGHT), UiPalette.TEXT_PRIMARY, false);
        g.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        for (int row = 0; row < visibleRows(); row++) {
            int index = first + row;
            if (index >= entries.size()) break;
            Rect rect = row(row);
            boolean selected = entries.get(index).id().equals(selectedId);
            boolean hovered = rect.contains(mouseX, mouseY);
            if (selected) {
                g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0x66517497);
            }
            if (isHeld(index, hovered)) g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0x22000000);
            else if (hovered) g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiPalette.SURFACE_HOVER_OVERLAY);
            if (selected) g.fill(rect.x(), rect.y(), rect.x() + 2, rect.bottom(), UiPalette.BUTTON_PRIMARY);
            if (keyboardFocus && focused == index) UiSkinRenderer.drawOutline(g, rect.x(), rect.y(), rect.width(), rect.height(), UiPalette.OUTLINE_HOVER);
            String full = entries.get(index).label().getString();
            String shown = ellipsize(font, full, rect.width() - 12);
            g.drawString(font, shown, rect.x() + 6, UiSkinRenderer.centeredTextY(font, rect.y(), rect.height()), UiPalette.TEXT_PRIMARY, false);
            if (hovered && !shown.equals(full)) tooltip = entries.get(index).label();
        }
        if (visibleRows() > 0) UiSkinRenderer.drawMiniScrollbar(g, bounds.right() - 7, listTop(),
            visibleRows() * ROW_HEIGHT, visibleRows(), entries.size(), first);
        Rect manage = manage();
        g.fill(manage.x(), manage.y() - 4, manage.right(), manage.y() - 3, UiPalette.OUTLINE);
        boolean hoverManage = manage.contains(mouseX, mouseY);
        if (isHeld(entries.size(), hoverManage)) g.fill(manage.x(), manage.y(), manage.right(), manage.bottom(), 0x22000000);
        else if (hoverManage) g.fill(manage.x(), manage.y(), manage.right(), manage.bottom(), UiPalette.SURFACE_HOVER_OVERLAY);
        if (keyboardFocus && focused == entries.size()) UiSkinRenderer.drawOutline(g, manage.x(), manage.y(), manage.width(), manage.height(), UiPalette.OUTLINE_HOVER);
        String full = CategoryChoices.label("manage").getString();
        String shown = ellipsize(font, full, manage.width() - 8);
        g.drawString(font, shown, manage.x() + 4, UiSkinRenderer.centeredTextY(font, manage.y(), manage.height()), UiPalette.TEXT_PRIMARY, false);
        if (hoverManage && !shown.equals(full)) tooltip = CategoryChoices.label("manage");
        g.disableScissor();
        return tooltip;
    }

    public boolean contains(double x, double y) { return bounds.contains(x, y); }
    public boolean dragging() { return dragging; }
    public boolean pressed() { return dragging || held >= 0; }

    public void press(double x, double y) {
        cancelPress();
        if (!bounds.contains(x, y)) return;
        held = hit(x, y);
        if (held >= 0) focused = held;
        if (visibleRows() > 0 && entries.size() > visibleRows() && x >= bounds.right() - 10
            && y >= listTop() && y < listTop() + visibleRows() * ROW_HEIGHT) {
            int track = visibleRows() * ROW_HEIGHT;
            int thumb = Math.max(8, track * visibleRows() / entries.size());
            int top = listTop() + (track - thumb) * first / (entries.size() - visibleRows());
            thumbGrab = y >= top && y < top + thumb ? y - top : thumb / 2.0;
            dragging = true;
            held = -1;
            drag(y);
        }
    }

    public CategoryChoices.Entry release(double x, double y) {
        int chosen = held;
        boolean accept = !dragging && chosen >= 0 && chosen == hit(x, y);
        cancelPress();
        return accept ? entry(chosen) : null;
    }

    public void cancelPress() { held = -1; dragging = false; }

    public void drag(double y) {
        if (!dragging || visibleRows() == 0 || entries.size() <= visibleRows()) return;
        int track = visibleRows() * ROW_HEIGHT;
        int thumb = Math.max(8, track * visibleRows() / entries.size());
        first = (int) Math.round((y - listTop() - thumbGrab) * (entries.size() - visibleRows()) / Math.max(1, track - thumb));
        clampFirst();
    }

    public void scroll(double delta) {
        cancelPress();
        first -= (int) Math.signum(delta);
        clampFirst();
    }

    public CategoryChoices.Entry keyPressed(int key) {
        cancelPress();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) return entry(focused);
        if (key == GLFW.GLFW_KEY_UP) focused = Math.max(0, focused - 1);
        if (key == GLFW.GLFW_KEY_DOWN) focused = Math.min(entries.size(), focused + 1);
        if (key == GLFW.GLFW_KEY_HOME) focused = 0;
        if (key == GLFW.GLFW_KEY_END) focused = entries.size();
        revealFocused();
        return null;
    }

    boolean isHeld(int index, boolean hovered) { return !dragging && held == index && hovered; }

    public void focusSelection() {
        focused = 0;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(selectedId)) focused = i;
        revealFocused();
    }

    private int listTop() { return bounds.y() + ManagerContentLayout.SIDEBAR_HEADER_HEIGHT + 6; }

    private CategoryChoices.Entry entry(int index) {
        return index == entries.size() ? new CategoryChoices.Entry(CategoryChoices.MANAGE, CategoryChoices.label("manage"))
            : index >= 0 && index < entries.size() ? entries.get(index) : null;
    }
    private int visibleRows() { return Math.min(entries.size(), Math.max(0, (bounds.height() - ManagerContentLayout.SIDEBAR_HEADER_HEIGHT - 38) / ROW_HEIGHT)); }
    private Rect row(int index) { return new Rect(bounds.x() + 4, listTop() + index * ROW_HEIGHT, Math.max(0, bounds.width() - 16), ROW_HEIGHT); }
    private Rect manage() { return new Rect(bounds.x() + 6, Math.max(bounds.y(), bounds.bottom() - 26), Math.max(0, bounds.width() - 12), 20); }
    private int hit(double x, double y) {
        if (!bounds.contains(x, y)) return -1;
        if (manage().contains(x, y)) return entries.size();
        for (int i = 0; i < visibleRows(); i++) if (row(i).contains(x, y)) return first + i;
        return -1;
    }
    private void clampFirst() { first = Math.max(0, Math.min(Math.max(0, entries.size() - visibleRows()), first)); }
    private void revealFocused() {
        if (focused < entries.size() && visibleRows() > 0) first = Math.max(focused - visibleRows() + 1, Math.min(first, focused));
        clampFirst();
    }
    private static String ellipsize(Font font, String value, int width) {
        if (width <= 0) return "";
        if (font.width("…") > width) return font.plainSubstrByWidth(value, width);
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }
}
