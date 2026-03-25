package com.starskyxiii.collapsible_groups.defaults;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.platform.Services;
import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.*;

import java.util.List;

public final class Generic implements DefaultGroupProvider {

	@Override
	public int priority() {
		return 100;
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!Services.CONFIG.loadDefaultGroups()) return List.of();
		if (!Services.CONFIG.loadGenericGroups())  return List.of();
		return List.of(
			group("__default_potions",           "Potions",           item("minecraft:potion")),
			group("__default_splash_potions",    "Splash Potions",    item("minecraft:splash_potion")),
			group("__default_lingering_potions", "Lingering Potions", item("minecraft:lingering_potion")),
			group("__default_tipped_arrows",     "Tipped Arrows",     item("minecraft:tipped_arrow")),
			group("__default_enchanted_books",   "Enchanted Books",   item("minecraft:enchanted_book")),
			group("__default_paintings",         "Paintings",         item("minecraft:painting")),
			group("__default_music_discs",       "Music Discs",       tag("c:music_discs")),
			group("__default_pottery_sherds",    "Pottery Sherds",    tag("minecraft:decorated_pot_sherds")),
			group("__default_trim_templates",    "Trim Templates",    tag("minecraft:trim_templates"))
		);
	}
}
