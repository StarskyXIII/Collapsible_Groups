package com.starskyxiii.collapsible_groups.compat.jei.ui;

import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import net.minecraft.network.chat.Component;

/** Shared styling for edit-box content that must not depend on Minecraft defaults. */
public final class OreUiEditBoxStyle {
	private OreUiEditBoxStyle() {}

	/**
	 * Gives hint text the same explicit color used by Ore UI input text.
	 *
	 * <p>Minecraft 26.1.2 applies a dark-gray default to unstyled hints, unlike
	 * 1.21.1 where they inherited the edit box text color. Keeping the color on
	 * the component preserves the intended appearance across both versions.
	 */
	public static Component hint(Component component) {
		int rgb = UiPalette.TEXT_PRIMARY & 0xFFFFFF;
		return component.copy().withStyle(style -> style.withColor(rgb));
	}
}
