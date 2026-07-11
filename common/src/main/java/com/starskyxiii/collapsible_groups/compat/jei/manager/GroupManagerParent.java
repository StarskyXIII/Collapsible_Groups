package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.core.SavedGroupContext;
import net.minecraft.client.gui.screens.Screen;

public interface GroupManagerParent {
	void onGroupSaved(SavedGroupContext context);

	Screen asScreen();
}
