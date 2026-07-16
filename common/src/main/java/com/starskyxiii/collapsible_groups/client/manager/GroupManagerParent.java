package com.starskyxiii.collapsible_groups.client.manager;

import com.starskyxiii.collapsible_groups.client.manager.model.SavedGroupContext;
import net.minecraft.client.gui.screens.Screen;

public interface GroupManagerParent {
	void onGroupSaved(SavedGroupContext context);

	Screen asScreen();
}
