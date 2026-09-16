package com.starskyxiii.collapsible_groups.client.manager;

import com.starskyxiii.collapsible_groups.client.widget.EditorChrome;
import com.starskyxiii.collapsible_groups.client.widget.CommandPress;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.persistence.GroupCategoryStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class CategoryPickerScreen extends Screen {
    private static final int ROW = 20;
    private final Screen parent;
    private final GroupCategoryStore store;
    private final CategoryMoveState state;
    private final Runnable moved;
    private final CommandPress<String> press = new CommandPress<>();
    private EditBox search;
    private Object sources, preferences;
    private final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();
    private long observedRevision;
    private final List<GroupChangeEvent.Subscription> subscriptions = new ArrayList<>();
    private int first, focusedRow = -1, focus;
    private Component message;
    private boolean dragging;
    private double thumbOffset;

    public CategoryPickerScreen(Screen parent, List<String> ids, GroupCategoryStore store, Runnable moved) {
        super(CategoryChoices.label("move"));
        this.parent = parent;
        this.store = store;
        this.state = new CategoryMoveState(ids);
        this.moved = moved;
    }

    @Override protected void init() {
        if (subscriptions.isEmpty()) for (var kind : GroupChangeEvent.Kind.values())
            subscriptions.add(GroupChangeEvent.subscribe(kind, revision::incrementAndGet));
        focusedRow = -1;
        if (parent.width != width || parent.height != height) {
            parent.resize(minecraft, width, height);
            parent.removed();
        }
        press.clear();
        dragging = false;
        String query = search == null ? "" : search.getValue();
        var box = searchRect();
        search = new EditBox(font, box.x() + 4, box.y() + 4, Math.max(1, box.width() - 8), 12, CategoryChoices.label("search"));
        search.setBordered(false);
        search.setMaxLength(128);
        search.setHint(CategoryChoices.label("search"));
        search.setValue(query);
        search.setResponder(value -> {
            press.clear();
            state.search(value);
            first = 0;
            focusedRow = -1;
        });
        focus = 0;
        search.setFocused(true);
        refresh();
        first = Math.max(0, Math.min(first, maxFirst()));
    }

    private EditorChrome.Rect panel() {
        int w = Math.min(360, Math.max(1, width - 16)), h = Math.min(300, Math.max(1, height - 16));
        return new EditorChrome.Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    private EditorChrome.Rect searchRect() {
        var p = panel();
        return new EditorChrome.Rect(p.x() + 10, p.y() + 28, Math.max(1, p.width() - 20), 20);
    }

    private EditorChrome.Rect listRect() {
        var p = panel();
        return new EditorChrome.Rect(p.x() + 10, p.y() + 54, Math.max(1, p.width() - 28), Math.max(0, p.height() - 108));
    }

    private EditorChrome.Rect buttonRect(boolean confirm) {
        var p = panel();
        int w = Math.min(100, Math.max(1, (p.width() - 28) / 2));
        return new EditorChrome.Rect(p.right() - 10 - w - (confirm ? 0 : w + 8), p.bottom() - 30, w, 20);
    }

    private int rows() { return listRect().height() / ROW; }
    private int maxFirst() { return Math.max(0, state.visible().size() - rows()); }

    private void refresh() {
        var resourceData = GroupRepository.resourceData();
        var snapshot = store.snapshot();
        long currentRevision = revision.get();
        if (resourceData == sources && snapshot == preferences && currentRevision == observedRevision) return;
        observedRevision = currentRevision;
        sources = resourceData;
        preferences = snapshot;
        press.clear();
        dragging = false;
        List<CategoryChoices.Entry> entries = new ArrayList<>();
        entries.add(new CategoryChoices.Entry(CategoryChoices.UNCATEGORIZED, CategoryChoices.label("uncategorized")));
        entries.add(new CategoryChoices.Entry(CategoryChoices.FOLLOW_SOURCE, CategoryChoices.label("follow_source")));
        entries.addAll(CategoryChoices.available(snapshot, resourceData));
        state.entries(entries);
        if (!state.targets().isEmpty() && state.targets().stream().noneMatch(id -> GroupRepository.findById(id).isPresent())) {
            state.validate(java.util.Set.of());
            message = CategoryChoices.label("targets_changed");
        }
        first = Math.min(first, maxFirst());
        focusedRow = -1;
    }

    private boolean canMove() { return store.writable() && state.canMove(); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        refresh();
        parent.render(g, Integer.MIN_VALUE, Integer.MIN_VALUE, delta);
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(0, 0, width, height, UiPalette.SCREEN_SCRIM);
        var p = panel();
        g.fill(p.x(), p.y(), p.right(), p.bottom(), UiPalette.SURFACE);
        UiSkinRenderer.drawOutline(g, p.x(), p.y(), p.width(), p.height(), UiPalette.OUTLINE);
        g.drawCenteredString(font, title, width / 2, p.y() + 10, UiPalette.TEXT_PRIMARY);
        var s = searchRect();
        g.fill(s.x(), s.y(), s.right(), s.bottom(), UiPalette.SURFACE_DARK);
        UiSkinRenderer.drawOutline(g, s.x(), s.y(), s.width(), s.height(), search.isFocused() ? UiPalette.OUTLINE_SELECTED : UiPalette.OUTLINE_DARK);
        search.render(g, mouseX, mouseY, delta);
        var list = listRect();
        Component tooltip = null;
        for (int row = 0; row < rows() && first + row < state.visible().size(); row++) {
            int index = first + row, y = list.y() + row * ROW;
            var entry = state.visible().get(index);
            boolean hot = list.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW;
            boolean selected = entry.id().equals(state.selected());
            if (selected || hot) g.fill(list.x(), y, list.right(), y + ROW, selected ? UiPalette.CARD_BODY_HOVER : UiPalette.SURFACE_DARK);
            if (selected) g.fill(list.x(), y + 3, list.x() + 2, y + ROW - 3, UiPalette.OUTLINE_SELECTED);
            if (focus == 1 && focusedRow == index) UiSkinRenderer.drawOutline(g, list.x(), y, list.width(), ROW, UiPalette.OUTLINE_HOVER);
            String text = entry.label().getString();
            int available = Math.max(1, list.width() - 12);
            String clipped = font.width(text) > available ? font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…" : text;
            g.drawString(font, clipped, list.x() + 6, y + (ROW - font.lineHeight) / 2, UiPalette.TEXT_PRIMARY, false);
            if (hot && !text.equals(clipped)) tooltip = entry.label();
        }
        if (state.visible().isEmpty()) g.drawCenteredString(font, CategoryChoices.label("no_results"), width / 2, list.y() + 8, UiPalette.TEXT_HINT);
        UiSkinRenderer.drawMiniScrollbar(g, list.right() + 2, list.y(), rows() * ROW, rows(), state.visible().size(), first);
        Component hint = message != null ? message : CategoryChoices.label("move_count", state.targets().size());
        g.drawString(font, font.plainSubstrByWidth(hint.getString(), Math.max(1, p.width() - 20)), p.x() + 10, p.bottom() - 46, UiPalette.TEXT_HINT, false);
        drawButton(g, false, mouseX, mouseY);
        drawButton(g, true, mouseX, mouseY);
        if (tooltip != null) g.renderTooltip(font, tooltip, mouseX, mouseY);
        g.pose().popPose();
    }

    private void drawButton(GuiGraphics g, boolean confirm, int x, int y) {
        var r = buttonRect(confirm);
        String command = confirm ? "move" : "cancel";
        Component label = confirm ? CategoryChoices.label("move_confirm") : Component.translatable("collapsible_groups.button.cancel");
        UiSkinRenderer.drawButton(g, font, r.x(), r.y(), r.width(), r.height(), label.getString(),
            UiSkinRenderer.buttonState(!confirm || canMove(), false, r.contains(x, y), press.isHeld(command)));
        if (focus == (confirm ? 3 : 2)) UiSkinRenderer.drawOutline(g, r.x(), r.y(), r.width(), r.height(), UiPalette.OUTLINE_HOVER);
    }

    private String commandAt(double x, double y) {
        if (buttonRect(false).contains(x, y)) return "cancel";
        return canMove() && buttonRect(true).contains(x, y) ? "move" : null;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return true;
        refresh();
        press.begin(commandAt(x, y));
        if (buttonRect(false).contains(x, y) || buttonRect(true).contains(x, y)) return true;
        search.setFocused(searchRect().contains(x, y));
        focus = search.isFocused() ? 0 : -1;
        if (search.isFocused()) { search.mouseClicked(x, y, button); return true; }
        var list = listRect();
        if (x >= list.right() && x < list.right() + 8 && y >= list.y() && y < list.y() + rows() * ROW && maxFirst() > 0) {
            int h = rows() * ROW, thumb = Math.max(8, h * rows() / state.visible().size());
            int top = list.y() + (h - thumb) * first / maxFirst();
            thumbOffset = y >= top && y < top + thumb ? y - top : thumb / 2.0;
            dragging = true;
            dragTo(y);
        } else if (list.contains(x, y)) {
            int row = (int) (y - list.y()) / ROW, index = first + row;
            if (row < rows() && index < state.visible().size()) {
                state.select(state.visible().get(index).id());
                focusedRow = index;
            }
        }
        return true;
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) {
            refresh();
            String action = press.release(commandAt(x, y));
            dragging = false;
            if ("cancel".equals(action)) onClose();
            else if ("move".equals(action)) move();
        }
        return true;
    }

    private void move() {
        refresh();
        var existing = state.targets().stream().filter(id -> GroupRepository.findById(id).isPresent()).collect(Collectors.toSet());
        var validation = state.validate(existing);
        if (validation == CategoryMoveState.Validation.TARGETS_CHANGED) { message = CategoryChoices.label("targets_changed"); return; }
        if (validation != CategoryMoveState.Validation.READY || !store.writable()) return;
        String target = state.selected();
        boolean saved = store.update(preferences -> CategoryChoices.FOLLOW_SOURCE.equals(target)
            ? preferences.followSource(state.targets())
            : preferences.assign(state.targets(), CategoryChoices.UNCATEGORIZED.equals(target) ? null : target));
        if (!saved) { message = CategoryChoices.label("save_failed"); return; }
        moved.run();
        onClose();
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging) dragTo(y);
        return true;
    }

    private void dragTo(double y) {
        press.clear();
        int h = rows() * ROW, thumb = Math.max(8, h * rows() / Math.max(1, state.visible().size()));
        first = Math.max(0, Math.min(maxFirst(), (int) Math.round((y - listRect().y() - thumbOffset) * maxFirst() / Math.max(1, h - thumb))));
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        press.clear();
        first = Math.max(0, Math.min(maxFirst(), first - (int) Math.signum(vertical)));
        if (focusedRow < first || focusedRow >= first + rows()) focusedRow = -1;
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        refresh();
        press.clear();
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_TAB) {
            focus = Math.floorMod(focus + (hasShiftDown() ? -1 : 1), 4);
            search.setFocused(focus == 0);
            if (focus == 1 && rows() > 0 && !state.visible().isEmpty()
                && (focusedRow < first || focusedRow >= first + rows())) focusedRow = first;
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (rows() == 0 || state.visible().isEmpty()) return true;
            focus = 1;
            search.setFocused(false);
            focusedRow = Math.max(0, Math.min(state.visible().size() - 1, focusedRow + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            if (focusedRow < first) first = focusedRow;
            if (focusedRow >= first + rows()) first = Math.min(maxFirst(), focusedRow - rows() + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE && focus != 0) {
            if (focus == 3) move();
            else if (focus == 2) onClose();
            else if (focus == 1 && focusedRow >= 0 && focusedRow < state.visible().size()) state.select(state.visible().get(focusedRow).id());
            return true;
        }
        return search.isFocused() && search.keyPressed(key, scan, modifiers);
    }

    @Override public boolean charTyped(char c, int modifiers) { return search.isFocused() && search.charTyped(c, modifiers); }
    @Override public void removed() {
        press.clear();
        subscriptions.forEach(GroupChangeEvent.Subscription::close);
        subscriptions.clear();
        super.removed();
    }
    @Override public void onClose() { press.clear(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
