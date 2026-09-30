package com.starskyxiii.collapsible_groups.client.widget;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class CompatScreen extends Screen {
    private MouseButtonEvent activeMouse;
    private boolean doubleClick;
    protected CompatScreen(Component title) { super(title); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        activeMouse = event;
        this.doubleClick = doubleClick;
        try { return InputEvents.withMouse(event, doubleClick, () -> mouseClicked(event.x(), event.y(), event.button())); }
        finally { activeMouse = null; this.doubleClick = false; }
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        activeMouse = event;
        try { return InputEvents.withMouse(event, false, () -> mouseReleased(event.x(), event.y(), event.button())); }
        finally { activeMouse = null; }
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        activeMouse = event;
        try { return InputEvents.withMouse(event, false, () -> mouseDragged(event.x(), event.y(), event.button(), dx, dy)); }
        finally { activeMouse = null; }
    }
    @Override public boolean keyPressed(KeyEvent event) { return InputEvents.withModifiers(event.modifiers(), () -> keyPressed(event.key(), event.scancode(), event.modifiers())); }
    @Override public boolean charTyped(CharacterEvent event) { return charTyped(event.codepoint(), InputEvents.modifiers()); }
    public boolean mouseClicked(double x, double y, int button) { return super.mouseClicked(mouse(x, y, button), doubleClick); }
    public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(mouse(x, y, button)); }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return super.mouseDragged(mouse(x, y, button), dx, dy); }
    public boolean keyPressed(int key, int scan, int modifiers) { return super.keyPressed(new KeyEvent(key, scan, modifiers)); }
    public boolean charTyped(int character, int modifiers) { return super.charTyped(new CharacterEvent(character)); }
    protected static boolean hasShiftDown() { return InputEvents.shiftDown(); }
    private MouseButtonEvent mouse(double x, double y, int button) { return activeMouse == null ? InputEvents.mouse(x, y, button) : activeMouse; }
}
