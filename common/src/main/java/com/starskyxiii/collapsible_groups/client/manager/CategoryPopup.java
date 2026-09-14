package com.starskyxiii.collapsible_groups.client.manager;

import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class CategoryPopup {
    private static final int ROW_HEIGHT = 20;
    private final List<CategoryChoices.Entry> entries;
    private final int x;
    private final int y;
    private final int width;
    private final int visibleRows;
    private int first;
    private int focused;
    private boolean dragging;
    private double thumbGrabOffset;

    public CategoryPopup(List<CategoryChoices.Entry> entries, String selectedId, int anchorX, int anchorY, int screenWidth, int screenHeight) {
        this.entries = List.copyOf(entries);
        width = Math.min(228, Math.max(1, screenWidth - 12));
        visibleRows = Math.min(entries.size(), Math.max(1, (screenHeight - 20) / ROW_HEIGHT));
        x = Math.max(6, Math.min(anchorX, screenWidth - width - 6));
        y = Math.max(6, Math.min(anchorY, screenHeight - height() - 6));
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(selectedId)) focused = i;
        first = Math.max(0, focused - visibleRows + 1);
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(x, y, x + width, y + height(), UiPalette.SURFACE);
        UiSkinRenderer.drawOutline(graphics, x, y, width, height(), UiPalette.OUTLINE);
        String tooltip = null;
        for (int row = 0; row < visibleRows; row++) {
            int index = first + row;
            if (index >= entries.size()) break;
            int top = y + 4 + row * ROW_HEIGHT;
            boolean hovered = contains(mouseX, mouseY) && mouseY >= top && mouseY < top + ROW_HEIGHT;
            String full = entries.get(index).label().getString();
            String text = font.width(full) > width - 24
                ? font.plainSubstrByWidth(full, Math.max(0, width - 24 - font.width("…"))) + "…" : full;
            UiSkinRenderer.drawSegment(graphics, font, x + 4, top, width - 16, ROW_HEIGHT,
                text, hovered ? UiSkinRenderer.ButtonState.HOVERED
                    : focused == index ? UiSkinRenderer.ButtonState.SELECTED : UiSkinRenderer.ButtonState.NORMAL);
            if (hovered && !text.equals(full)) tooltip = full;
        }
        UiSkinRenderer.drawMiniScrollbar(graphics, x + width - 9, y + 4, visibleRows * ROW_HEIGHT,
            visibleRows, entries.size(), first);
        if (tooltip != null) graphics.renderTooltip(font, net.minecraft.network.chat.Component.literal(tooltip), mouseX, mouseY);
        graphics.pose().popPose();
    }

    public CategoryChoices.Entry clicked(double mouseX, double mouseY) {
        if (contains(mouseX, mouseY) && mouseX >= x + width - 12 && entries.size() > visibleRows) {
            int trackHeight = visibleRows * ROW_HEIGHT;
            int thumbHeight = Math.max(8, trackHeight * visibleRows / entries.size());
            int top = y + 4 + (trackHeight - thumbHeight) * first / (entries.size() - visibleRows);
            thumbGrabOffset = mouseY >= top && mouseY < top + thumbHeight ? mouseY - top : thumbHeight / 2.0;
            dragging = true;
            drag(mouseY);
            return null;
        }
        if (!contains(mouseX, mouseY) || mouseX >= x + width - 12 || mouseY < y + 4) return null;
        int row = (int) (mouseY - y - 4) / ROW_HEIGHT;
        return row >= 0 && row < visibleRows && first + row < entries.size() ? entries.get(first + row) : null;
    }

    public CategoryChoices.Entry keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) return entries.get(focused);
        if (key == GLFW.GLFW_KEY_UP) focused = Math.max(0, focused - 1);
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_TAB) focused = Math.min(entries.size() - 1, focused + 1);
        if (key == GLFW.GLFW_KEY_HOME) focused = 0;
        if (key == GLFW.GLFW_KEY_END) focused = entries.size() - 1;
        first = Math.max(0, Math.min(first, focused));
        first = Math.max(first, focused - visibleRows + 1);
        return null;
    }

    public void scroll(double delta) {
        first = Math.max(0, Math.min(entries.size() - visibleRows, first - (int) Math.signum(delta)));
        focused = Math.max(first, Math.min(first + visibleRows - 1, focused));
    }

    public void release() { dragging = false; }

    public void drag(double mouseY) {
        if (!dragging) return;
        int trackHeight = visibleRows * ROW_HEIGHT;
        int thumbHeight = Math.max(8, trackHeight * visibleRows / entries.size());
        first = Math.max(0, Math.min(entries.size() - visibleRows,
            (int) Math.round((mouseY - y - 4 - thumbGrabOffset) * (entries.size() - visibleRows) / Math.max(1, trackHeight - thumbHeight))));
        focused = Math.max(first, Math.min(first + visibleRows - 1, focused));
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height();
    }

    private int height() { return visibleRows * ROW_HEIGHT + 8; }
}
