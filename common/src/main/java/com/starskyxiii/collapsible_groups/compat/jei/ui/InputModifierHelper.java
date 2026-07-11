package com.starskyxiii.collapsible_groups.compat.jei.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

public final class InputModifierHelper {
	private InputModifierHelper() {}

	public static boolean controlDown() {
		return keyDown(341) || keyDown(345);
	}

	public static boolean shiftDown() {
		return keyDown(340) || keyDown(344);
	}

	private static boolean keyDown(int keyCode) {
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), keyCode);
	}
}
