package com.starskyxiii.collapsible_groups.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Registers the Fabric config screen with Mod Menu.
 * This class is only loaded when Mod Menu is present (declared as a {@code modmenu}
 * entrypoint in {@code fabric.mod.json}).
 */
public class ModMenuApiImpl implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return FabricConfigScreen::new;
	}
}
