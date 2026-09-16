package com.starskyxiii.collapsible_groups.client.widget;

import com.starskyxiii.collapsible_groups.config.ColorConfigParser;
import com.starskyxiii.collapsible_groups.config.SettingsSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.function.IntConsumer;

public final class ColorPicker {
    private static final int[] SWATCHES = {
        0xFFFFFFFF, 0xFFCED2D6, 0xFF9DA3A9, 0xFF6B7178,
        0xFF3A3E44, 0xFF1E1F21, 0xFFE84C4C, 0xFFF08A3C,
        0xFFF2C744, 0xFF5ABF4A, 0xFF3FA9C4, 0xFF3C7DDB,
        0xFF6F5AE0, 0xFFB05AD6, 0xFFE066A6, 0xFF7A5A44
    };
    private final Font font;
    private final CommandPress<String> press = new CommandPress<>();
    private final Component title;
    private final boolean rgb;
    private final IntConsumer onChange;
    private final Runnable onCancel;
    private final EditBox hex;
    private EditorChrome.Rect bounds;
    private int color;
    private int draggingChannel = -1;
    private boolean open = true;
    private boolean syncing;

    public ColorPicker(Font font, Component title, int initial, boolean rgb,
                       IntConsumer onChange, Runnable onCancel, EditorChrome.Rect available) {
        this.font = font;
        this.title = title;
        this.rgb = rgb;
        this.color = rgb ? 0xFF000000 | initial & 0xFFFFFF : initial;
        this.onChange = onChange;
        this.onCancel = onCancel;
        hex = new EditBox(font, 0, 0, 1, 12, Component.literal(rgb ? "#RRGGBB" : "#AARRGGBB"));
        hex.setBordered(false);
        hex.setMaxLength(10);
        setBounds(available);
        hex.setValue(SettingsSnapshot.hex(color, rgb));
        hex.setResponder(this::typed);
        hex.setFocused(true);
    }

    public boolean isOpen() { return open; }
    public void clearFocus() { hex.setFocused(false); }
    public void cancel() { press.clear(); onCancel.run(); open = false; draggingChannel = -1; }

    public void setBounds(EditorChrome.Rect available) {
        press.clear();
        int w = Math.min(266, Math.max(120, available.width() - 16));
        int h = Math.min(196, Math.max(120, available.height() - 16));
        bounds = new EditorChrome.Rect(available.x() + (available.width() - w) / 2,
            available.y() + (available.height() - h) / 2, w, h);
        var field = hexBounds();
        hex.setPosition(field.x() + 4, UiSkinRenderer.textFieldTextY(font, field.y(), field.height()) + 1);
        hex.setWidth(Math.max(1, field.width() - 8));
        draggingChannel = -1;
    }

    private int paletteY() { return bounds.y() + 8 + font.lineHeight + 6; }
    private int slidersY() { return paletteY() + 39; }
    private int sliderWidth() { return Math.max(24, Math.min(132, bounds.width() - 80)); }
    private EditorChrome.Rect hexBounds() {
        return new EditorChrome.Rect(bounds.x() + 66, slidersY() + 82, bounds.width() - 74, 18);
    }
    private boolean valid() { return ColorConfigParser.isValidArgb(hex.getValue()); }

    private void typed(String value) {
        if (syncing || !valid()) return;
        color = rgb ? 0xFF000000 | ColorConfigParser.parseRgb(value, color) : ColorConfigParser.parseArgb(value, color);
        onChange.accept(color);
    }

    private void setColor(int value) {
        color = rgb ? 0xFF000000 | value & 0xFFFFFF : value;
        syncing = true;
        hex.setValue(SettingsSnapshot.hex(color, rgb));
        syncing = false;
        onChange.accept(color);
    }

    private int channel(int channel) { return color >>> (channel == 3 ? 24 : 16 - channel * 8) & 255; }

    private void drag(double mouseX) {
        int value = Math.max(0, Math.min(255, (int) Math.round((mouseX - bounds.x() - 24) * 255 / sliderWidth())));
        int shift = draggingChannel == 3 ? 24 : 16 - draggingChannel * 8;
        setColor(color & ~(255 << shift) | value << shift);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        UiSkinRenderer.drawPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        UiSkinRenderer.drawOutline(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), UiPalette.OUTLINE_SELECTED);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), bounds.width() - 16), bounds.x() + 8,
            bounds.y() + 8, UiPalette.TEXT_PRIMARY, false);
        for (int i = 0; i < SWATCHES.length; i++) {
            int x = bounds.x() + 8 + i % 8 * 17;
            int y = paletteY() + i / 8 * 17;
            graphics.fill(x, y, x + 14, y + 14, SWATCHES[i]);
            UiSkinRenderer.drawOutline(graphics, x, y, 14, 14,
                contains(x, y, 14, 14, mouseX, mouseY) ? UiPalette.OUTLINE_HOVER : UiPalette.OUTLINE_DARK);
        }
        graphics.fill(bounds.right() - 28, paletteY() - 1, bounds.right() - 10, paletteY() + 17, color);
        UiSkinRenderer.drawOutline(graphics, bounds.right() - 28, paletteY() - 1, 18, 18, UiPalette.OUTLINE_DARK);
        for (int channel = 0; channel < 4; channel++) {
            int y = slidersY() + channel * 20;
            boolean active = !rgb || channel != 3;
            graphics.drawString(font, "RGBA".substring(channel, channel + 1), bounds.x() + 8,
                UiSkinRenderer.centeredTextY(font, y, 16), active ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED, false);
            boolean hot = draggingChannel == channel || active && UiSkinRenderer.sliderHitBand(bounds.x() + 24, y + 2, sliderWidth()).contains(mouseX, mouseY);
            UiSkinRenderer.drawSlider(graphics, bounds.x() + 24, y + 2, sliderWidth(), channel(channel), 255, active, hot);
            graphics.drawString(font, Integer.toString(channel(channel)), bounds.x() + 32 + sliderWidth(),
                UiSkinRenderer.centeredTextY(font, y, 16), active ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED, false);
        }
        var field = hexBounds();
        graphics.drawString(font, rgb ? "#RRGGBB" : "#AARRGGBB", bounds.x() + 8,
            UiSkinRenderer.centeredTextY(font, field.y(), 18), UiPalette.TEXT_MUTED, false);
        graphics.fill(field.x(), field.y(), field.right(), field.bottom(), UiPalette.SURFACE_DARK);
        int outline = !valid() ? 0xFFFF6B5F : hex.isFocused() ? UiPalette.OUTLINE_SELECTED : UiPalette.OUTLINE_DARK;
        UiSkinRenderer.drawOutline(graphics, field.x(), field.y(), field.width(), field.height(), outline);
        hex.setTextColor(valid() ? UiPalette.TEXT_PRIMARY : 0xFFFF6B5F);
        hex.render(graphics, mouseX, mouseY, 0);
        button(graphics, bounds.right() - 127, "collapsible_groups.editor.rules.picker.confirm", valid(), mouseX, mouseY);
        button(graphics, bounds.right() - 66, "collapsible_groups.button.cancel", true, mouseX, mouseY);
    }

    private void button(GuiGraphics graphics, int x, String key, boolean enabled, int mouseX, int mouseY) {
        boolean hot = contains(x, bounds.bottom() - 28, 58, 20, mouseX, mouseY);
        UiSkinRenderer.drawButton(graphics, font, x, bounds.bottom() - 28, 58, 20, Component.translatable(key).getString(),
            UiSkinRenderer.buttonState(enabled, false, hot, press.isHeld(key)));
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return true;
        press.begin(commandAt(x, y));
        if (press.target() != null || contains(bounds.right() - 127, bounds.bottom() - 28, 58, 20, x, y)) return true;
        if (!bounds.contains(x, y)) {
            if (valid()) open = false;
            return true;
        }
        hex.setFocused(hexBounds().contains(x, y));
        if (hex.isFocused()) { hex.mouseClicked(x, y, button); return true; }
        for (int i = 0; i < SWATCHES.length; i++) {
            if (contains(bounds.x() + 8 + i % 8 * 17, paletteY() + i / 8 * 17, 14, 14, x, y)) {
                setColor(color & 0xFF000000 | SWATCHES[i] & 0xFFFFFF);
                return true;
            }
        }
        for (int channel = 0; channel < (rgb ? 3 : 4); channel++) {
            if (UiSkinRenderer.sliderHitBand(bounds.x() + 24, slidersY() + channel * 20 + 2, sliderWidth()).contains(x, y)) {
                draggingChannel = channel;
                drag(x);
                return true;
            }
        }
        return true;
    }

    public boolean mouseDragged(double x) {
        if (draggingChannel >= 0) drag(x);
        return true;
    }

    private String commandAt(double x, double y) {
        if (contains(bounds.right() - 66, bounds.bottom() - 28, 58, 20, x, y)) return "collapsible_groups.button.cancel";
        if (valid() && contains(bounds.right() - 127, bounds.bottom() - 28, 58, 20, x, y)) return "collapsible_groups.editor.rules.picker.confirm";
        return null;
    }

    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) {
            String command = press.release(commandAt(x, y));
            if ("collapsible_groups.button.cancel".equals(command)) cancel();
            else if (command != null) open = false;
            draggingChannel = -1;
        }
        return true;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        press.clear();
        if (key == GLFW.GLFW_KEY_ESCAPE) cancel();
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { if (valid()) open = false; }
        else hex.keyPressed(key, scan, modifiers);
        return true;
    }

    public boolean charTyped(char character, int modifiers) { hex.charTyped(character, modifiers); return true; }

    private static boolean contains(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
