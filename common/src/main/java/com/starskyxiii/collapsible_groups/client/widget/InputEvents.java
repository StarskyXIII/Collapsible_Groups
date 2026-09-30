package com.starskyxiii.collapsible_groups.client.widget;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import java.util.function.BooleanSupplier;

public final class InputEvents {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private InputEvents() {}

    public static boolean shiftDown() { return (modifiers() & GLFW.GLFW_MOD_SHIFT) != 0; }
    public static boolean controlDown() { return (modifiers() & net.minecraft.client.input.InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER) != 0; }
    public static boolean altDown() { return (modifiers() & GLFW.GLFW_MOD_ALT) != 0; }
    public static int modifiers() {
        Context current = CURRENT.get();
        if (current != null) return current.modifiers();
        return (keyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || keyDown(GLFW.GLFW_KEY_RIGHT_SHIFT) ? GLFW.GLFW_MOD_SHIFT : 0)
            | (keyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || keyDown(GLFW.GLFW_KEY_RIGHT_CONTROL) ? GLFW.GLFW_MOD_CONTROL : 0)
            | (keyDown(GLFW.GLFW_KEY_LEFT_ALT) || keyDown(GLFW.GLFW_KEY_RIGHT_ALT) ? GLFW.GLFW_MOD_ALT : 0)
            | (keyDown(GLFW.GLFW_KEY_LEFT_SUPER) || keyDown(GLFW.GLFW_KEY_RIGHT_SUPER) ? GLFW.GLFW_MOD_SUPER : 0);
    }
    public static boolean withMouse(MouseButtonEvent event, boolean doubleClick, BooleanSupplier action) {
        return withContext(new Context(event.modifiers(), doubleClick), action);
    }
    public static boolean withModifiers(int modifiers, BooleanSupplier action) {
        return withContext(new Context(modifiers, false), action);
    }
    public static boolean doubleClick() { return CURRENT.get() != null && CURRENT.get().doubleClick(); }
    public static MouseButtonEvent mouse(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers()));
    }
    private static boolean keyDown(int key) { return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key); }
    private static boolean withContext(Context context, BooleanSupplier action) {
        Context previous = CURRENT.get();
        CURRENT.set(context);
        try { return action.getAsBoolean(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    private record Context(int modifiers, boolean doubleClick) {}
}
