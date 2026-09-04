package com.starskyxiii.collapsible_groups.defaults.integration;

import com.starskyxiii.collapsible_groups.config.NeoForgeConfig;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.group;
import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.itemWithComponent;

/** Built-in component-aware gem groups for Iron's Apothic. */
public final class IronsApothic implements DefaultGroupProvider {
	@Override
	public int priority() {
		return 410;
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!NeoForgeConfig.shouldLoadIronsApothic()) return List.of();

		List<GroupDefinition> groups = new ArrayList<>();
		groups.add(group("__default_irons_apothic_gem_core_bloody_pearl", "Bloody Pearl Gems", gem("irons_apothic:core/bloody_pearl")));
		groups.add(group("__default_irons_apothic_gem_core_celsius_tear", "Celsius' Tear Gems", gem("irons_apothic:core/celsius_tear")));
		groups.add(group("__default_irons_apothic_gem_core_charged_lodestone", "Charged Lodestone Gems", gem("irons_apothic:core/charged_lodestone")));
		groups.add(group("__default_irons_apothic_gem_core_dragonfire_spessartite", "Dragonfire Spessartite Gems", gem("irons_apothic:core/dragonfire_spessartite")));
		groups.add(group("__default_irons_apothic_gem_core_effervescent_brimstone", "Effervescent Brimstone Gems", gem("irons_apothic:core/effervescent_brimstone")));
		groups.add(group("__default_irons_apothic_gem_core_golems_onyx", "Golem's Onyx Gems", gem("irons_apothic:core/golems_onyx")));
		groups.add(group("__default_irons_apothic_gem_core_luminous_opal", "Luminous Opal Gems", gem("irons_apothic:core/luminous_opal")));
		groups.add(group("__default_irons_apothic_gem_core_mossy_agate", "Mossy Agate Gems", gem("irons_apothic:core/mossy_agate")));
		groups.add(group("__default_irons_apothic_gem_core_void_umbalite", "Void Umbalite Gems", gem("irons_apothic:core/void_umbalite")));
		groups.add(group("__default_irons_apothic_gem_core_whispering_tentaculite", "Whispering Tentaculite Gems", gem("irons_apothic:core/whispering_tentaculite")));

		addExternal(groups, "monsterspellbooks", group("__default_irons_apothic_gem_external_aeolian_celestite", "Aeolian Celestite Gems", gem("irons_apothic:external/aeolian_celestite")));
		addExternal(groups, "iss_magicfromtheeast", group("__default_irons_apothic_gem_external_amethyst_lotus", "Amethyst Lotus Gems", gem("irons_apothic:external/amethyst_lotus")));
		addExternal(groups, "ess_requiem", group("__default_irons_apothic_gem_external_arcanists_edge", "Arcanist's Edge Gems", gem("irons_apothic:external/arcanists_edge")));
		addExternal(groups, "cataclysm_spellbooks", group("__default_irons_apothic_gem_external_deepsea_heartstone", "Deepsea Heartstone Gems", gem("irons_apothic:external/deepsea_heartstone")));
		addExternal(groups, "discerning_the_eldritch", group("__default_irons_apothic_gem_external_goetic_stibnite", "Goetic Stibnite Gems", gem("irons_apothic:external/goetic_stibnite")));
		addExternal(groups, "iss_magicfromtheeast", group("__default_irons_apothic_gem_external_harmonic_geode", "Harmonic Geode Gems", gem("irons_apothic:external/harmonic_geode")));
		addExternal(groups, "aces_spell_utils", group("__default_irons_apothic_gem_external_lithographic_moissanite", "Lithographic Moissanite Gems", gem("irons_apothic:external/lithographic_moissanite")));
		addExternal(groups, "hazentouvelib", group("__default_irons_apothic_gem_external_mycotoxin_crystal", "Mycotoxin Crystal Gems", gem("irons_apothic:external/mycotoxin_crystal")));
		addExternal(groups, "tunes_n_tomes", group("__default_irons_apothic_gem_external_resonant_quartz", "Resonant Quartz Gems", gem("irons_apothic:external/resonant_quartz")));
		addExternal(groups, "monsterspellbooks", group("__default_irons_apothic_gem_external_sepulchral_howlite", "Sepulchral Howlite Gems", gem("irons_apothic:external/sepulchral_howlite")));
		addExternal(groups, "aces_spell_utils", group("__default_irons_apothic_gem_external_sirens_aquamarine", "Siren's Aquamarine Gems", gem("irons_apothic:external/sirens_aquamarine")));
		addExternal(groups, "aero_additions", group("__default_irons_apothic_gem_external_sky_meteoritill", "Sky Meteoritill Gems", gem("irons_apothic:external/sky_meteoritill")));
		addExternal(groups, "hazentouvelib", group("__default_irons_apothic_gem_external_umbral_obsidian", "Umbral Obsidian Gems", gem("irons_apothic:external/umbral_obsidian")));
		return List.copyOf(groups);
	}

	private static GroupFilter gem(String gemId) {
		return itemWithComponent("apotheosis:gem", "apotheosis:gem", gemId);
	}

	private static void addExternal(List<GroupDefinition> groups, String requiredModId, GroupDefinition group) {
		if (ModList.get().isLoaded(requiredModId)) groups.add(group);
	}
}
