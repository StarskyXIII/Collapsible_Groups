package com.starskyxiii.collapsible_groups.client.manager;

import com.starskyxiii.collapsible_groups.client.widget.ConfirmDialog;
import com.starskyxiii.collapsible_groups.client.widget.CommandPress;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.persistence.GroupCategoryStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

public final class CategoryManagerScreen extends Screen {
    private static final int LIST_TOP = 34;
    private static final int ROW_HEIGHT = 28;
    private final Screen parent;
    private final CommandPress<String> press = new CommandPress<>();
    private final GroupCategoryStore store = GroupCategoryStore.current();
    private List<CategoryChoices.Entry> entries = List.of();
    private int scroll;
    private Object sourceSnapshot;
    private Object preferenceSnapshot;
    private boolean dragging;
    private double thumbGrabOffset;
    private String editingId;
    private String deletingId;
    private EditBox nameField;
    private Component message;

    public CategoryManagerScreen(Screen parent) {
        super(CategoryChoices.label("manage"));
        this.parent = parent;
        store.reload();
    }

    @Override protected void init() {
        press.clear();
        String text = nameField == null ? "" : nameField.getValue();
        clearWidgets();
        refreshEntries();
        if (editingId != null) createNameField(text);
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private void refreshEntries() {
        var sources = GroupRepository.resourceData();
        var preferences = store.snapshot();
        if (sources == sourceSnapshot && preferences == preferenceSnapshot) return;
        press.clear();
        dragging = false;
        sourceSnapshot = sources;
        preferenceSnapshot = preferences;
        entries = CategoryChoices.available(preferences, sources);
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private boolean categoryExists(String id) {
        return entries.stream().anyMatch(entry -> entry.id().equals(id));
    }

    private void createNameField(String value) {
        var bounds = ConfirmDialog.bounds(width, height);
        nameField = new EditBox(font, bounds.x() + 18, bounds.y() + 42, bounds.width() - 36, 12, CategoryChoices.label("name"));
        nameField.setBordered(false);
        nameField.setTextColor(UiPalette.TEXT_PRIMARY);
        nameField.setMaxLength(128);
        nameField.setValue(value);
        nameField.setResponder(text -> { press.clear(); nameField.setTextColor(UiPalette.TEXT_PRIMARY); message = null; });
        nameField.setFocused(true);
        addRenderableWidget(nameField);
        setFocused(nameField);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, UiPalette.SCREEN_SCRIM);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshEntries();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        UiSkinRenderer.drawScreenBars(graphics, width, height, 29, 28);
        int surfaceX = editingId != null || deletingId != null ? Integer.MIN_VALUE : mouseX;
        button(graphics, 6, 5, 50, Component.translatable("collapsible_groups.manager.btn_back"), true, surfaceX, mouseY);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(0, width - 176)),
            64, 11, UiPalette.TEXT_PRIMARY, false);
        button(graphics, width - 108, 5, 102, CategoryChoices.label("create"), store.writable(), surfaceX, mouseY);
        graphics.enableScissor(6, LIST_TOP, width - 6, listBottom());
        int listMouseX = mouseY >= LIST_TOP && mouseY < listBottom() ? surfaceX : Integer.MIN_VALUE;
        for (int i = 0; i < entries.size(); i++) {
            int y = LIST_TOP + i * ROW_HEIGHT - scroll;
            if (y + ROW_HEIGHT < LIST_TOP || y > listBottom()) continue;
            var entry = entries.get(i);
            boolean local = entry.id().startsWith("local:");
            graphics.fill(6, y, width - 16, y + ROW_HEIGHT - 2, UiPalette.SURFACE);
            graphics.drawString(font, font.plainSubstrByWidth(entry.label().getString(), Math.max(0, width - 184)),
                12, y + 9, UiPalette.TEXT_PRIMARY, false);
            button(graphics, width - 162, y + 3, 64, CategoryChoices.label("rename"), store.writable(), listMouseX, mouseY);
            button(graphics, width - 94, y + 3, 74, CategoryChoices.label(local ? "delete" : "reset_name"),
                store.writable() && (local || store.snapshot().sourceNames().containsKey(entry.id())), listMouseX, mouseY);
        }
        graphics.disableScissor();
        UiSkinRenderer.drawScrollbarPixels(graphics, width - 12, LIST_TOP, listBottom() - LIST_TOP,
            listBottom() - LIST_TOP, entries.size() * ROW_HEIGHT, scroll);
        Component footer = message != null ? message : !store.writable() ? CategoryChoices.label("read_only") : CategoryChoices.label("local_hint");
        graphics.drawString(font, font.plainSubstrByWidth(footer.getString(), Math.max(0, width - 12)), 6, height - 18, UiPalette.TEXT_HINT, false);
        if (editingId != null) {
            ConfirmDialog.render(graphics, font, width, height, CategoryChoices.label(editingId.isEmpty() ? "create" : "rename"),
                List.of(), Component.translatable("collapsible_groups.button.save"), Component.translatable("collapsible_groups.button.cancel"),
                mouseX, mouseY, canConfirm(), dialogHeld());
            var bounds = ConfirmDialog.bounds(width, height);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 501);
            graphics.fill(bounds.x() + 12, bounds.y() + 36, bounds.x() + bounds.width() - 12, bounds.y() + 62, UiPalette.SURFACE_DARK);
            UiSkinRenderer.drawOutline(graphics, bounds.x() + 12, bounds.y() + 36, bounds.width() - 24, 26, UiPalette.OUTLINE_SELECTED);
            nameField.render(graphics, mouseX, mouseY, partialTick);
            if (message != null) graphics.drawString(font, font.plainSubstrByWidth(message.getString(), bounds.width() - 24),
                bounds.x() + 12, bounds.y() + 66, 0xFFFF6060, false);
            graphics.pose().popPose();
        } else if (deletingId != null) {
            ConfirmDialog.render(graphics, font, width, height, CategoryChoices.label("delete"),
                message == null ? List.of(CategoryChoices.label("delete_body")) : List.of(CategoryChoices.label("delete_body"), message), CategoryChoices.label("delete"),
                Component.translatable("collapsible_groups.button.cancel"), mouseX, mouseY, canConfirm(), dialogHeld());
        }
    }

    private void button(GuiGraphics graphics, int x, int y, int w, Component label, boolean enabled, int mouseX, int mouseY) {
        UiSkinRenderer.drawButton(graphics, font, x, y, w, 20, label.getString(),
            UiSkinRenderer.buttonState(enabled, false, inside(mouseX, mouseY, x, y, w, 20), press.isHeld(commandAt(mouseX, mouseY))));
    }

    private boolean canConfirm() {
        refreshEntries();
        return store.writable() && (deletingId == null || categoryExists(deletingId))
            && (editingId == null || (editingId.isEmpty() || categoryExists(editingId))
                && nameField != null && !nameField.getValue().trim().isEmpty());
    }

    private ConfirmDialog.Action dialogHeld() {
        return press.isHeld("confirm") ? ConfirmDialog.Action.PRIMARY
            : press.isHeld("cancel") ? ConfirmDialog.Action.SECONDARY : ConfirmDialog.Action.NONE;
    }

    private String commandAt(double x, double y) {
        if (editingId != null || deletingId != null) {
            var action = ConfirmDialog.hitTest(width, height, x, y);
            if (action == ConfirmDialog.Action.SECONDARY) return "cancel";
            if (action == ConfirmDialog.Action.PRIMARY && canConfirm()) return "confirm";
            return null;
        }
        if (inside(x, y, 6, 5, 50, 20)) return "back";
        if (!store.writable()) return null;
        if (inside(x, y, width - 108, 5, 102, 20)) return "create";
        if (y < LIST_TOP || y >= listBottom()) return null;
        int index = (int) (y - LIST_TOP + scroll) / ROW_HEIGHT;
        if (index < 0 || index >= entries.size()) return null;
        var entry = entries.get(index);
        int top = LIST_TOP + index * ROW_HEIGHT - scroll + 3;
        if (inside(x, y, width - 162, top, 64, 20)) return "rename|" + entry.id();
        if (inside(x, y, width - 94, top, 74, 20)) {
            if (entry.id().startsWith("local:")) return "delete|" + entry.id();
            if (store.snapshot().sourceNames().containsKey(entry.id())) return "reset|" + entry.id();
        }
        return null;
    }

    private void execute(String action) {
        switch (action) {
            case "back" -> onClose();
            case "create" -> edit("", "");
            case "confirm" -> confirm();
            case "cancel" -> closeDialog();
            default -> {
                int separator = action.indexOf('|');
                String id = action.substring(separator + 1);
                switch (action.substring(0, separator)) {
                    case "rename" -> entries.stream().filter(entry -> entry.id().equals(id)).findFirst()
                        .ifPresent(entry -> edit(id, entry.label().getString()));
                    case "delete" -> deletingId = id;
                    case "reset" -> apply(store.update(current -> current.resetName(id)));
                }
            }
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        refreshEntries();
        if (button != 0) return true;
        press.begin(commandAt(x, y));
        if (press.target() != null) return true;
        if (editingId != null || deletingId != null) {
            if (editingId != null) super.mouseClicked(x, y, button);
            return true;
        }
        if (y < LIST_TOP || y >= listBottom()) return true;
        if (x >= width - 14 && x < width - 6 && maxScroll() > 0) {
            int trackHeight = listBottom() - LIST_TOP;
            int thumbHeight = Math.max(14, trackHeight * trackHeight / (entries.size() * ROW_HEIGHT));
            int top = LIST_TOP + (trackHeight - thumbHeight) * scroll / maxScroll();
            thumbGrabOffset = y >= top && y < top + thumbHeight ? y - top : thumbHeight / 2.0;
            dragging = true;
            dragTo(y);
        }
        return true;
    }

    private void edit(String id, String value) {
        press.clear();
        message = null;
        editingId = id;
        createNameField(value);
    }

    private void confirm() {
        if (!canConfirm()) return;
        if (editingId != null) {
            String name = nameField.getValue().trim();
            if (name.isEmpty()) { nameField.setTextColor(0xFFFF6060); message = CategoryChoices.label("name_required"); return; }
            String id = editingId;
            boolean saved = store.update(current -> id.isEmpty() ? current.create("local:" + UUID.randomUUID(), name) : current.rename(id, name));
            if (!saved) { message = CategoryChoices.label("save_failed"); nameField.setTextColor(0xFFFF6060); return; }
        } else if (deletingId != null && !store.update(current -> current.delete(deletingId))) {
            message = CategoryChoices.label("save_failed");
            return;
        }
        message = null;
        closeDialog();
        init();
    }

    private void apply(boolean success) {
        message = success ? null : CategoryChoices.label("save_failed");
        init();
    }

    private void closeDialog() {
        press.clear();
        editingId = null;
        deletingId = null;
        nameField = null;
        clearWidgets();
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        refreshEntries();
        if (button == 0) {
            String action = press.release(commandAt(x, y));
            if (action != null) execute(action);
            dragging = false;
        }
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (editingId != null) { super.mouseDragged(x, y, button, dx, dy); return true; }
        if (deletingId != null) return true;
        if (dragging) dragTo(y);
        return true;
    }
    private void dragTo(double y) {
        press.clear();
        int trackHeight = listBottom() - LIST_TOP;
        int thumbHeight = Math.max(14, trackHeight * trackHeight / Math.max(1, entries.size() * ROW_HEIGHT));
        scroll = Math.max(0, Math.min(maxScroll(), (int) Math.round(
            (y - LIST_TOP - thumbGrabOffset) * maxScroll() / Math.max(1, trackHeight - thumbHeight))));
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        press.clear();
        if (editingId == null && deletingId == null) scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (dy * ROW_HEIGHT)));
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        press.clear();
        if (editingId != null || deletingId != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) closeDialog();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) confirm();
            else if (editingId != null) super.keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        if (editingId != null) super.charTyped(character, modifiers);
        return true;
    }
    @Override public void onClose() {
        press.clear();
        if (editingId != null || deletingId != null) closeDialog();
        else Minecraft.getInstance().setScreen(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
    private int listBottom() { return Math.max(LIST_TOP, height - 32); }
    private int maxScroll() { return Math.max(0, entries.size() * ROW_HEIGHT - (listBottom() - LIST_TOP)); }
    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
