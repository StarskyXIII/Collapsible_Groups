package com.starskyxiii.collapsible_groups.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class CompatEditBox extends EditBox {
    public CompatEditBox(Font font, int x, int y, int width, int height, Component message) { super(font, x, y, width, height, message); }
    public CompatEditBox(Font font, int width, int height, Component message) { super(font, width, height, message); }
    @Override public void setHint(Component hint) { super.setHint(hint.copy().withColor(UiPalette.TEXT_PRIMARY & 0xFFFFFF)); }
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) { extractRenderState(graphics, mouseX, mouseY, delta); }
    public boolean mouseClicked(double x, double y, int button) { return super.mouseClicked(InputEvents.mouse(x, y, button), InputEvents.doubleClick()); }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return super.mouseDragged(InputEvents.mouse(x, y, button), dx, dy); }
    public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(InputEvents.mouse(x, y, button)); }
    public boolean keyPressed(int key, int scan, int modifiers) { return super.keyPressed(new KeyEvent(key, scan, modifiers)); }
    public boolean charTyped(int character, int modifiers) { return super.charTyped(new CharacterEvent(character)); }
}
