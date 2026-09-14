package com.starskyxiii.collapsible_groups.client.config;

import com.starskyxiii.collapsible_groups.client.widget.ColorPicker;
import com.starskyxiii.collapsible_groups.client.widget.EditorChrome;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.config.SettingsController;
import com.starskyxiii.collapsible_groups.config.SettingsDraft;
import com.starskyxiii.collapsible_groups.config.SettingsSnapshot;
import com.starskyxiii.collapsible_groups.platform.Services;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public final class GroupConfigScreen extends Screen {
    private static final List<List<String>> KEYS = List.of(
        List.of("defaultGroups.enabled", "ui.showManagerButton", "ui.searchUngroupSmallGroups", "ui.searchUngroupThreshold"),
        List.of("ui.showGroupBackgrounds", "ui.collapsedGroupBackgroundColor", "ui.expandedGroupBackgroundColor",
            "ui.groupNameColor", "ui.expandedGroupBorderColor"),
        List.of("debug.enableTimingLogs", "debug.verifyStartupIndex", "debug.verifyEditorPreviewIndex"));
    private static final int TOP = 60;
    private static final int ROW = 38;
    private final Screen parent;
    private final SettingsController controller;
    private final SettingsDraft draft;
    private int page;
    private int scroll;
    private EditBox threshold;
    private ColorPicker picker;
    private boolean dragging;
    private double grab;
    private Component message;

    public GroupConfigScreen(Screen parent) {
        super(Component.translatable("collapsible_groups.config.screen_title"));
        this.parent = parent;
        controller = Services.CONFIG.settings();
        if (controller.initialized()) controller.reload();
        else controller.initialize();
        draft = new SettingsDraft(controller.snapshot());
    }

    @Override protected void init() {
        clearWidgets();
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        threshold = null;
        if (page == 0) {
            threshold = new EditBox(font, right() - 92, rowY(3) + 13, 76, 12,
                Component.translatable("collapsible_groups.configuration.ui.searchUngroupThreshold"));
            threshold.setBordered(false);
            threshold.setMaxLength(16);
            threshold.setValue(draft.searchUngroupThreshold);
            threshold.setResponder(value -> { draft.searchUngroupThreshold = value; message = null; });
            addWidget(threshold);
        }
        if (picker != null) picker.setBounds(new EditorChrome.Rect(0, 0, width, height));
        dragging = false;
    }

    private int left() { return (width - Math.min(520, width - 12)) / 2; }
    private int right() { return width - left(); }
    private int bottom() { return Math.max(TOP + 1, height - 52); }
    private int rowY(int index) { return TOP + index * ROW - scroll; }
    private int maxScroll() { return Math.max(0, KEYS.get(page).size() * ROW - (bottom() - TOP)); }
    private boolean modal() { return picker != null && picker.isOpen(); }
    private Component label(String key) { return Component.translatable("collapsible_groups.configuration." + key); }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, UiPalette.SCREEN_SCRIM);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        UiSkinRenderer.drawScreenBars(graphics, width, height, 29, 48);
        graphics.drawCenteredString(font, title, width / 2, 11, UiPalette.TEXT_PRIMARY);
        int surfaceX = modal() ? Integer.MIN_VALUE : mouseX;
        int tabW = (right() - left()) / 3;
        String[] tabs = {"general", "appearance", "advanced"};
        for (int i = 0; i < 3; i++) {
            int x = left() + i * tabW;
            UiSkinRenderer.drawButton(graphics, font, x, 33, tabW, 20, text("tab." + tabs[i]).getString(),
                page == i ? UiSkinRenderer.ButtonState.SELECTED : contains(x, 33, tabW, 20, surfaceX, mouseY)
                    ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL);
        }
        Component tooltip = null;
        graphics.enableScissor(left(), TOP, right(), bottom());
        int rowMouseX = mouseY >= TOP && mouseY < bottom() ? surfaceX : Integer.MIN_VALUE;
        for (int i = 0; i < KEYS.get(page).size(); i++) {
            int y = rowY(i);
            if (y + ROW < TOP || y > bottom()) continue;
            String key = KEYS.get(page).get(i);
            graphics.fill(left(), y, right() - 10, y + ROW - 3, UiPalette.SURFACE);
            var lines = font.split(label(key), Math.max(20, right() - left() - 138));
            for (int line = 0; line < Math.min(2, lines.size()); line++)
                graphics.drawString(font, lines.get(line), left() + 8, y + (lines.size() > 1 ? 8 : 14) + line * 10, UiPalette.TEXT_PRIMARY, false);
            boolean hot = contains(left(), y, right() - left() - 10, ROW - 3, rowMouseX, mouseY);
            if (hot) tooltip = Component.translatable("collapsible_groups.configuration." + key + ".tooltip");
            if (page == 0 && i == 3) {
                graphics.fill(right() - 98, y + 7, right() - 12, y + 29, UiPalette.SURFACE_DARK);
                UiSkinRenderer.drawOutline(graphics, right() - 98, y + 7, 86, 22,
                    !draft.valid() ? 0xFFFF6B5F : threshold.isFocused() ? UiPalette.OUTLINE_SELECTED : UiPalette.OUTLINE_DARK);
                threshold.setTextColor(draft.valid() ? UiPalette.TEXT_PRIMARY : 0xFFFF6B5F);
                threshold.render(graphics, rowMouseX, mouseY, partialTick);
            } else if (page == 1 && i > 0) {
                int color = color(i);
                UiSkinRenderer.drawButton(graphics, font, right() - 104, y + 8, 92, 20,
                    SettingsSnapshot.hex(color, i == 3), hot ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL);
                graphics.fill(right() - 122, y + 11, right() - 108, y + 25, i == 3 ? color | 0xFF000000 : color);
                UiSkinRenderer.drawOutline(graphics, right() - 122, y + 11, 14, 14, UiPalette.OUTLINE_DARK);
            } else UiSkinRenderer.drawSwitch(graphics, right() - 58, y + 7, 44, 22, enabled(i), true, hot, false);
        }
        graphics.disableScissor();
        UiSkinRenderer.drawScrollbarPixels(graphics, right() - 6, TOP, bottom() - TOP,
            bottom() - TOP, KEYS.get(page).size() * ROW, scroll);
        Component status = message;
        if (status == null && !controller.writable()) status = text("read_failed");
        if (status == null && controller.result() == SettingsController.Result.APPLY_PENDING) status = text("apply_pending");
        if (status == null && !draft.valid()) status = text("invalid_threshold");
        if (status != null) {
            graphics.drawString(font, font.plainSubstrByWidth(status.getString(), width - 12), 6, height - 43, 0xFFFFB077, false);
            if (mouseY >= height - 48 && mouseY < height - 29) tooltip = status;
        }
        button(graphics, width / 2 - 88, height - 27, 84, Component.translatable("collapsible_groups.button.save"),
            draft.valid() && controller.writable(), surfaceX, mouseY);
        button(graphics, width / 2 + 4, height - 27, 84, Component.translatable("collapsible_groups.button.cancel"),
            true, surfaceX, mouseY);
        if (modal()) {
            graphics.fill(0, 0, width, height, 0x66000000);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 500);
            picker.render(graphics, mouseX, mouseY);
            graphics.pose().popPose();
        } else if (tooltip != null) graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private static Component text(String key) { return Component.translatable("collapsible_groups.config." + key); }

    private void button(GuiGraphics graphics, int x, int y, int w, Component text, boolean enabled, int mouseX, int mouseY) {
        UiSkinRenderer.drawButton(graphics, font, x, y, w, 20, text.getString(),
            !enabled ? UiSkinRenderer.ButtonState.DISABLED : contains(x, y, w, 20, mouseX, mouseY)
                ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL);
    }

    private boolean enabled(int index) {
        if (page == 0) return switch (index) {
            case 0 -> draft.loadDefaultGroups; case 1 -> draft.showManagerButton; default -> draft.searchUngroupSmallGroups;
        };
        if (page == 1) return draft.showGroupBackgrounds;
        return switch (index) {
            case 0 -> draft.debugTimingEnabled; case 1 -> draft.debugStartupIndexVerificationEnabled; default -> draft.debugEditorIndexVerificationEnabled;
        };
    }

    private void toggle(int index) {
        if (page == 0) {
            switch (index) {
                case 0 -> draft.loadDefaultGroups = !draft.loadDefaultGroups;
                case 1 -> draft.showManagerButton = !draft.showManagerButton;
                default -> draft.searchUngroupSmallGroups = !draft.searchUngroupSmallGroups;
            }
        } else if (page == 1) draft.showGroupBackgrounds = !draft.showGroupBackgrounds;
        else {
            switch (index) {
                case 0 -> draft.debugTimingEnabled = !draft.debugTimingEnabled;
                case 1 -> draft.debugStartupIndexVerificationEnabled = !draft.debugStartupIndexVerificationEnabled;
                default -> draft.debugEditorIndexVerificationEnabled = !draft.debugEditorIndexVerificationEnabled;
            }
        }
    }

    private int color(int index) {
        return switch (index) {
            case 1 -> draft.collapsedGroupBackgroundColor; case 2 -> draft.expandedGroupBackgroundColor;
            case 3 -> draft.groupNameColor; default -> draft.expandedGroupBorderColor;
        };
    }

    private void color(int index, int value) {
        switch (index) {
            case 1 -> draft.collapsedGroupBackgroundColor = value; case 2 -> draft.expandedGroupBackgroundColor = value;
            case 3 -> draft.groupNameColor = value & 0xFFFFFF; default -> draft.expandedGroupBorderColor = value;
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (modal()) return picker.mouseClicked(x, y, button);
        if (button != 0) return true;
        if (contains(width / 2 + 4, height - 27, 84, 20, x, y)) { onClose(); return true; }
        if (contains(width / 2 - 88, height - 27, 84, 20, x, y)) {
            if (!draft.valid() || !controller.writable()) return true;
            SettingsController.Result result = controller.save(draft.snapshot());
            if (result == SettingsController.Result.SUCCESS) onClose();
            else message = text(switch (result) {
                case READ_FAILED -> "read_failed"; case SAVE_FAILED -> "save_failed"; default -> "apply_pending";
            });
            return true;
        }
        int tabW = (right() - left()) / 3;
        for (int i = 0; i < 3; i++) if (contains(left() + i * tabW, 33, tabW, 20, x, y)) {
            page = i; scroll = 0; init(); return true;
        }
        if (y < TOP || y >= bottom()) return true;
        if (maxScroll() > 0 && x >= right() - 8 && x < right()) {
            int visible = bottom() - TOP;
            double thumbHeight = Math.max(14, visible * (double) visible / (KEYS.get(page).size() * ROW));
            double thumbY = TOP + scroll * (visible - thumbHeight) / maxScroll();
            grab = y >= thumbY && y < thumbY + thumbHeight ? y - thumbY : thumbHeight / 2;
            dragging = true;
            dragScroll(y);
            return true;
        }
        if (threshold != null) {
            threshold.setFocused(contains(right() - 98, rowY(3) + 7, 86, 22, x, y));
            if (threshold.isFocused()) { setFocused(threshold); return threshold.mouseClicked(x, y, button); }
        }
        for (int i = 0; i < KEYS.get(page).size(); i++) if (contains(left(), rowY(i), right() - left() - 10, ROW - 3, x, y)) {
            if (page == 0 && i == 3) return true;
            if (page == 1 && i > 0) {
                int index = i, previous = color(i);
                picker = new ColorPicker(font, label(KEYS.get(page).get(i)), previous, i == 3,
                    value -> color(index, value), () -> color(index, previous), new EditorChrome.Rect(0, 0, width, height));
            } else toggle(i);
            message = null;
            return true;
        }
        return true;
    }

    private void setScroll(int value) {
        scroll = Math.max(0, Math.min(maxScroll(), value));
        if (threshold != null) threshold.setPosition(right() - 92, rowY(3) + 13);
    }

    private void dragScroll(double y) {
        int visible = bottom() - TOP;
        double thumbHeight = Math.max(14, visible * (double) visible / (KEYS.get(page).size() * ROW));
        setScroll((int) Math.round((y - TOP - grab) * maxScroll() / Math.max(1, visible - thumbHeight)));
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (modal()) return picker.mouseDragged(x);
        if (dragging && button == 0) { dragScroll(y); return true; }
        return threshold != null && threshold.isFocused() && threshold.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (modal()) return picker.mouseReleased();
        dragging = false;
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!modal() && x >= left() && x < right() && y >= TOP && y < bottom()) setScroll(scroll - (int) (vertical * 20));
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (modal()) return picker.keyPressed(key, scan, modifiers);
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return threshold != null && threshold.isFocused() ? threshold.keyPressed(key, scan, modifiers) : super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        if (modal()) return picker.charTyped(character, modifiers);
        return threshold != null && threshold.isFocused() && threshold.charTyped(character, modifiers);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    private static boolean contains(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}

