package com.starskyxiii.collapsible_groups.compat.jei.manager;

import net.minecraft.client.gui.screens.Screen;

public interface GroupManagerParent {
	void onGroupSaved();

	Screen asScreen();
}
