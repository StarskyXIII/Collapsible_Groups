package com.starskyxiii.collapsible_groups.defaults.integration;

import com.starskyxiii.collapsible_groups.config.NeoForgeConfig;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.*;

import java.util.List;

/**
 * Built-in groups for Apotheosis.
 * One group per gem type, matching all purity levels via HasComponent filter.
 * Automatically skipped if Apotheosis is not installed.
 * Twilight Forest gem groups are always registered; they will simply be empty
 * if the Twilight Forest mod is not present.
 */
public final class Apotheosis implements DefaultGroupProvider {

	@Override
	public int priority() {
		return 400;
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!NeoForgeConfig.shouldLoadApotheosis()) return List.of();
		return List.of(
			group("__default_apotheosis_gem_core_ballast",    "Ballast Gems",       component("apotheosis:gem", "apotheosis:core/ballast")),
			group("__default_apotheosis_gem_core_brawlers",   "Brawler's Gems",      component("apotheosis:gem", "apotheosis:core/brawlers")),
			group("__default_apotheosis_gem_core_breach",     "Breach Gems",        component("apotheosis:gem", "apotheosis:core/breach")),
			group("__default_apotheosis_gem_core_combatant",  "Combatant Gems",     component("apotheosis:gem", "apotheosis:core/combatant")),
			group("__default_apotheosis_gem_core_guardian",   "Guardian Gems",      component("apotheosis:gem", "apotheosis:core/guardian")),
			group("__default_apotheosis_gem_core_lightning",  "Lightning Gems",     component("apotheosis:gem", "apotheosis:core/lightning")),
			group("__default_apotheosis_gem_core_lunar",      "Lunar Gems",         component("apotheosis:gem", "apotheosis:core/lunar")),
			group("__default_apotheosis_gem_core_samurai",    "Samurai Gems",       component("apotheosis:gem", "apotheosis:core/samurai")),
			group("__default_apotheosis_gem_core_slipstream", "Slipstream Gems",    component("apotheosis:gem", "apotheosis:core/slipstream")),
			group("__default_apotheosis_gem_core_solar",      "Solar Gems",         component("apotheosis:gem", "apotheosis:core/solar")),
			group("__default_apotheosis_gem_core_splendor",   "Splendor Gems",      component("apotheosis:gem", "apotheosis:core/splendor")),
			group("__default_apotheosis_gem_core_tyrannical", "Tyrannical Gems",    component("apotheosis:gem", "apotheosis:core/tyrannical")),
			group("__default_apotheosis_gem_core_warlord",    "Warlord Gems",       component("apotheosis:gem", "apotheosis:core/warlord")),
			group("__default_apotheosis_gem_overworld_earth",        "Earth Gems",        component("apotheosis:gem", "apotheosis:overworld/earth")),
			group("__default_apotheosis_gem_overworld_royalty",      "Royalty Gems",      component("apotheosis:gem", "apotheosis:overworld/royalty")),
			group("__default_apotheosis_gem_overworld_verdant_ruin", "Verdant Ruin Gems", component("apotheosis:gem", "apotheosis:overworld/verdant_ruin")),
			group("__default_apotheosis_gem_the_end_endersurge", "Endersurge Gems", component("apotheosis:gem", "apotheosis:the_end/endersurge")),
			group("__default_apotheosis_gem_the_end_mageslayer", "Mageslayer Gems", component("apotheosis:gem", "apotheosis:the_end/mageslayer")),
			group("__default_apotheosis_gem_the_nether_blood_lord",    "Blood Lord Gems",    component("apotheosis:gem", "apotheosis:the_nether/blood_lord")),
			group("__default_apotheosis_gem_the_nether_inferno",       "Inferno Gems",       component("apotheosis:gem", "apotheosis:the_nether/inferno")),
			group("__default_apotheosis_gem_the_nether_molten_breach", "Molten Breach Gems", component("apotheosis:gem", "apotheosis:the_nether/molten_breach")),
			group("__default_apotheosis_gem_twilight_forest", "TwilightForest Gems", component("apotheosis:gem", "apotheosis:twilight/forest")),
			group("__default_apotheosis_gem_twilight_queen",  "Frozen Queen Gems",  component("apotheosis:gem", "apotheosis:twilight/queen")),
			group("__default_apotheosis_potion_charm", "Potion Charms", item("apotheosis:potion_charm"))
		);
	}
}
