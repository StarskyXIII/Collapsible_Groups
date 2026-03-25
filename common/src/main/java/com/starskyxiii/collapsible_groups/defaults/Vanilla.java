package com.starskyxiii.collapsible_groups.defaults;

import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.platform.Services;
import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.*;

import java.util.List;

public final class Vanilla implements DefaultGroupProvider {

	@Override
	public int priority() {
		return 200;
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!Services.CONFIG.loadDefaultGroups()) return List.of();
		if (!Services.CONFIG.loadVanillaGroups())  return List.of();
		return List.of(
			group("__default_vanilla_colored_glass_blocks",  "Vanilla Colored Glass Blocks",  Filters.all(Filters.itemTag("c:glass_blocks"), Filters.itemTag("c:dyed"), Filters.itemNamespace("minecraft"))),
			group("__default_vanilla_colored_glass_panes",   "Vanilla Colored Glass Panes",   Filters.all(Filters.itemTag("c:glass_panes"), Filters.itemTag("c:dyed"), Filters.itemNamespace("minecraft"))),
			group("__default_vanilla_colored_shulker_boxes", "Vanilla Colored Shulker Boxes", Filters.all(Filters.itemTag("c:shulker_boxes"), Filters.itemTag("c:dyed"), Filters.itemNamespace("minecraft"))),
			group("__default_vanilla_wools",                 "Vanilla Wools",                 Filters.all(Filters.itemTag("minecraft:wool"), Filters.itemNamespace("minecraft"))),
			group("__default_vanilla_carpets",               "Vanilla Carpets",               Filters.all(Filters.itemTag("minecraft:wool_carpets"), Filters.itemNamespace("minecraft"))),
			group("__default_vanilla_terracottas",           "Vanilla Terracottas",           Filters.all(Filters.itemTag("minecraft:terracotta"), Filters.itemNamespace("minecraft"))),
			group("__default_vanilla_concretes",             "Vanilla Concretes",             Filters.all(Filters.itemTag("c:concretes"), Filters.itemNamespace("minecraft"))),
			group("__default_vanilla_concrete_powders",      "Vanilla Concrete Powders",      Filters.all(Filters.itemTag("c:concrete_powders"), Filters.itemNamespace("minecraft"))),
			group("__default_vanilla_glazed_terracottas",    "Vanilla Glazed Terracottas",    Filters.all(Filters.itemTag("c:glazed_terracottas"), Filters.itemNamespace("minecraft"))),
			group("__default_vanilla_beds",                  "Vanilla Beds",                  Filters.all(Filters.itemTag("minecraft:beds"), Filters.itemNamespace("minecraft"))),
			group("__default_vanilla_banners",               "Vanilla Banners",               Filters.all(Filters.itemTag("minecraft:banners"), Filters.itemNamespace("minecraft"))),
			group("__default_vanilla_candles",               "Vanilla Candles",               Filters.all(Filters.itemTag("minecraft:candles"), Filters.itemNamespace("minecraft")))
		);
	}
}
