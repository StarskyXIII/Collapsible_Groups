package com.starskyxiii.collapsible_groups.client.config;

import com.starskyxiii.collapsible_groups.client.manager.CategoryChoices;
import com.starskyxiii.collapsible_groups.client.widget.CommandPress;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.config.SettingsDraft;
import com.starskyxiii.collapsible_groups.group.GroupResourceLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BuiltinCategoryScreen extends Screen {
    private static final int TOP = 62, ROW = 24;
    private final Screen parent;
    private final SettingsDraft draft;
    private final List<CategoryChoices.Entry> entries;
    private List<CategoryChoices.Entry> visible;
    private final CommandPress<String> press = new CommandPress<>();
    private EditBox search;
    private int first, focusedRow = -1, focus;
    private boolean dragging;
    private double grab;

    public BuiltinCategoryScreen(Screen parent, SettingsDraft draft) {
        super(Component.translatable("collapsible_groups.config.builtin_categories"));
        this.parent = parent;
        this.draft = draft;
        var names = GroupResourceLoader.builtinCategories();
        List<CategoryChoices.Entry> result = new ArrayList<>();
        names.forEach((id, name) -> result.add(new CategoryChoices.Entry(id, name.toComponent())));
        draft.disabledBuiltinCategories.stream().filter(id -> !names.containsKey(id))
            .forEach(id -> result.add(new CategoryChoices.Entry(id, Component.literal(id))));
        result.sort(Comparator.comparing((CategoryChoices.Entry entry) -> entry.label().getString(), String.CASE_INSENSITIVE_ORDER).thenComparing(CategoryChoices.Entry::id));
        entries = List.copyOf(result);
        visible = entries;
    }

    private int left() { return Math.max(6, (width - 480) / 2); }
    private int right() { return width - left(); }
    private int rows() { return Math.max(0, (height - 42 - TOP) / ROW); }
    private int maxFirst() { return Math.max(0, visible.size() - rows()); }

    @Override protected void init() {
        press.clear();
        dragging = false;
        focusedRow = -1;
        String value = search == null ? "" : search.getValue();
        search = new EditBox(font, left() + 4, 38, Math.max(1, right() - left() - 8), 12, CategoryChoices.label("search"));
        search.setBordered(false);
        search.setMaxLength(128);
        search.setHint(CategoryChoices.label("search"));
        search.setValue(value);
        search.setResponder(query -> {
            String normalized = query.strip().toLowerCase(Locale.ROOT);
            visible = entries.stream().filter(entry -> entry.id().toLowerCase(Locale.ROOT).contains(normalized)
                || entry.label().getString().toLowerCase(Locale.ROOT).contains(normalized)).toList();
            first = 0;
            focusedRow = -1;
            press.clear();
        });
        focus = 0;
        search.setFocused(true);
        first = Math.min(first, maxFirst());
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, UiPalette.SCREEN_SCRIM);
        UiSkinRenderer.drawScreenBars(g, width, height, 29, 32);
        g.drawCenteredString(font, title, width / 2, 10, UiPalette.TEXT_PRIMARY);
        g.fill(left(), 34, right(), 54, UiPalette.SURFACE_DARK);
        UiSkinRenderer.drawOutline(g, left(), 34, right() - left(), 20, search.isFocused() ? UiPalette.OUTLINE_SELECTED : UiPalette.OUTLINE_DARK);
        search.render(g, mouseX, mouseY, delta);
        Component tooltip = null;
        for (int row = 0; row < rows() && first + row < visible.size(); row++) {
            int index = first + row, y = TOP + row * ROW;
            var entry = visible.get(index);
            boolean hot = contains(mouseX, mouseY, left(), y, right() - left() - 10, ROW - 2);
            g.fill(left(), y, right() - 10, y + ROW - 2, hot ? UiPalette.CARD_BODY_HOVER : UiPalette.SURFACE);
            boolean enabled = !draft.disabledBuiltinCategories.contains(entry.id());
            UiSkinRenderer.drawCheckbox(g, left() + 5, y + 4, enabled, hot);
            if (focus == 1 && focusedRow == index) UiSkinRenderer.drawOutline(g, left(), y, right() - left() - 10, ROW - 2, UiPalette.OUTLINE_HOVER);
            String text = entry.label().getString();
            int available = Math.max(1, right() - left() - 42);
            String clipped = font.width(text) > available ? font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…" : text;
            g.drawString(font, clipped, left() + 28, y + (ROW - font.lineHeight) / 2 - 1, UiPalette.TEXT_PRIMARY, false);
            if (hot && !clipped.equals(text)) tooltip = entry.label();
        }
        if (visible.isEmpty()) g.drawCenteredString(font, CategoryChoices.label("no_results"), width / 2, TOP + 12, UiPalette.TEXT_HINT);
        UiSkinRenderer.drawMiniScrollbar(g, right() - 6, TOP, rows() * ROW, rows(), visible.size(), first);
        UiSkinRenderer.drawButton(g, font, width / 2 - 50, height - 26, 100, 20,
            Component.translatable("collapsible_groups.manager.btn_back").getString(),
            UiSkinRenderer.buttonState(true, false, back(mouseX, mouseY), press.isHeld("back")));
        if (focus == 2) UiSkinRenderer.drawOutline(g, width / 2 - 50, height - 26, 100, 20, UiPalette.OUTLINE_HOVER);
        if (tooltip != null) g.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private boolean back(double x, double y) { return contains(x, y, width / 2 - 50, height - 26, 100, 20); }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return true;
        press.begin(back(x, y) ? "back" : null);
        if (back(x, y)) return true;
        search.setFocused(contains(x, y, left(), 34, right() - left(), 20));
        focus = search.isFocused() ? 0 : -1;
        if (search.isFocused()) { search.mouseClicked(x, y, button); return true; }
        if (contains(x, y, right() - 8, TOP, 8, rows() * ROW) && maxFirst() > 0) {
            int h = rows() * ROW, thumb = Math.max(8, h * rows() / visible.size());
            int top = TOP + (h - thumb) * first / maxFirst();
            grab = y >= top && y < top + thumb ? y - top : thumb / 2.0;
            dragging = true;
            dragTo(y);
        } else if (contains(x, y, left(), TOP, right() - left() - 10, rows() * ROW)) {
            if ((y - TOP) % ROW >= ROW - 2) return true;
            int index = first + (int) (y - TOP) / ROW;
            if (index < visible.size()) toggle(index);
        }
        return true;
    }

    private void toggle(int index) {
        String id = visible.get(index).id();
        if (!draft.disabledBuiltinCategories.remove(id)) draft.disabledBuiltinCategories.add(id);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) {
            dragging = false;
            if (press.release(back(x, y) ? "back" : null) != null) onClose();
        }
        return true;
    }

    private void dragTo(double y) {
        press.clear();
        int h = rows() * ROW, thumb = Math.max(8, h * rows() / Math.max(1, visible.size()));
        first = Math.max(0, Math.min(maxFirst(), (int) Math.round((y - TOP - grab) * maxFirst() / Math.max(1, h - thumb))));
        focusedRow = -1;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging) dragTo(y);
        return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double vertical) {
        press.clear();
        first = Math.max(0, Math.min(maxFirst(), first - (int) Math.signum(vertical)));
        if (focusedRow < first || focusedRow >= first + rows()) focusedRow = -1;
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        press.clear();
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_TAB) {
            focus = Math.floorMod(focus + (hasShiftDown() ? -1 : 1), 3);
            search.setFocused(focus == 0);
            if (focus == 1 && rows() > 0 && !visible.isEmpty() && (focusedRow < first || focusedRow >= first + rows())) focusedRow = first;
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (rows() == 0 || visible.isEmpty()) return true;
            focus = 1;
            search.setFocused(false);
            focusedRow = Math.max(0, Math.min(visible.size() - 1, focusedRow + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            if (focusedRow < first) first = focusedRow;
            if (focusedRow >= first + rows()) first = Math.min(maxFirst(), focusedRow - rows() + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE && focus != 0) {
            if (focus == 2) onClose();
            else if (focus == 1 && focusedRow >= 0 && focusedRow < visible.size()) toggle(focusedRow);
            return true;
        }
        return search.isFocused() && search.keyPressed(key, scan, modifiers);
    }

    @Override public boolean charTyped(char c, int modifiers) { return search.isFocused() && search.charTyped(c, modifiers); }
    @Override public void onClose() { press.clear(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private static boolean contains(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
