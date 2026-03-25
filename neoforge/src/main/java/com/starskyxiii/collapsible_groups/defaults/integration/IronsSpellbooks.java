package com.starskyxiii.collapsible_groups.defaults.integration;

import com.starskyxiii.collapsible_groups.config.NeoForgeConfig;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.*;

import java.util.List;

/**
 * Built-in scroll groups for Iron's Spellbooks.
 *
 * <p>Each group matches all scrolls for a single spell using:
 * <pre>
 *   all(
 *       itemId("irons_spellbooks:scroll"),
 *       itemComponentPath("irons_spellbooks:spell_container", "data[0].id", spellId)
 *   )
 * </pre>
 *
 * <p>The {@code itemId} pre-filter allows the query compiler to narrow
 * candidates via the item index before the component-path inspection runs.
 * This avoids a full-scan over all items for each scroll group.
 *
 * <h2>Integration assumptions</h2>
 * <ul>
 *   <li>The scroll item registry ID is {@code irons_spellbooks:scroll}.
 *   <li>The spell component is {@code irons_spellbooks:spell_container}.
 *   <li>The spell identifier is stored at {@code data[0].id}.
 *   <li>Scrolls always store exactly one spell at slot 0.
 * </ul>
 *
 * <h2>Spell list strategy (v1)</h2>
 * Spell IDs are explicitly hard-coded here. This is intentional:
 * runtime discovery would add complexity without meaningful benefit for v1.
 * When new spells are added to Iron's Spellbooks, update this list as a
 * normal maintenance task.
 *
 * <p>Automatically skipped if Iron's Spellbooks is not installed.
 */
public final class IronsSpellbooks implements DefaultGroupProvider {

	private static final String SCROLL_ITEM_ID = "irons_spellbooks:scroll";
	private static final String SPELL_COMPONENT = "irons_spellbooks:spell_container";
	private static final String SPELL_PATH = "data[0].id";

	@Override
	public int priority() {
		return 450;
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!NeoForgeConfig.shouldLoadIronsSpellbooks()) return List.of();
		return List.of(
			scroll("__default_irons_spellbooks_scroll_abyssal_shroud",    "Abyssal Shroud Scrolls",    "irons_spellbooks:abyssal_shroud"),
			scroll("__default_irons_spellbooks_scroll_acid_orb",          "Acid Orb Scrolls",          "irons_spellbooks:acid_orb"),
			scroll("__default_irons_spellbooks_scroll_acupuncture",       "Acupuncture Scrolls",       "irons_spellbooks:acupuncture"),
			scroll("__default_irons_spellbooks_scroll_angel_wing",        "Angel Wing Scrolls",        "irons_spellbooks:angel_wing"),
			scroll("__default_irons_spellbooks_scroll_arrow_volley",      "Arrow Volley Scrolls",      "irons_spellbooks:arrow_volley"),
			scroll("__default_irons_spellbooks_scroll_ascension",         "Ascension Scrolls",         "irons_spellbooks:ascension"),
			scroll("__default_irons_spellbooks_scroll_ball_lightning",    "Ball Lightning Scrolls",    "irons_spellbooks:ball_lightning"),
			scroll("__default_irons_spellbooks_scroll_black_hole",        "Black Hole Scrolls",        "irons_spellbooks:black_hole"),
			scroll("__default_irons_spellbooks_scroll_blaze_storm",       "Blaze Storm Scrolls",       "irons_spellbooks:blaze_storm"),
			scroll("__default_irons_spellbooks_scroll_blessing_of_life",  "Blessing of Life Scrolls",  "irons_spellbooks:blessing_of_life"),
			scroll("__default_irons_spellbooks_scroll_blight",            "Blight Scrolls",            "irons_spellbooks:blight"),
			scroll("__default_irons_spellbooks_scroll_blood_needles",     "Blood Needles Scrolls",     "irons_spellbooks:blood_needles"),
			scroll("__default_irons_spellbooks_scroll_blood_slash",       "Blood Slash Scrolls",       "irons_spellbooks:blood_slash"),
			scroll("__default_irons_spellbooks_scroll_blood_step",        "Blood Step Scrolls",        "irons_spellbooks:blood_step"),
			scroll("__default_irons_spellbooks_scroll_burning_dash",      "Burning Dash Scrolls",      "irons_spellbooks:burning_dash"),
			scroll("__default_irons_spellbooks_scroll_chain_creeper",     "Chain Creeper Scrolls",     "irons_spellbooks:chain_creeper"),
			scroll("__default_irons_spellbooks_scroll_chain_lightning",   "Chain Lightning Scrolls",   "irons_spellbooks:chain_lightning"),
			scroll("__default_irons_spellbooks_scroll_charge",            "Charge Scrolls",            "irons_spellbooks:charge"),
			scroll("__default_irons_spellbooks_scroll_cleanse",           "Cleanse Scrolls",           "irons_spellbooks:cleanse"),
			scroll("__default_irons_spellbooks_scroll_cloud_of_regeneration", "Cloud of Regeneration Scrolls", "irons_spellbooks:cloud_of_regeneration"),
			scroll("__default_irons_spellbooks_scroll_cone_of_cold",      "Cone of Cold Scrolls",      "irons_spellbooks:cone_of_cold"),
			scroll("__default_irons_spellbooks_scroll_counterspell",      "Counterspell Scrolls",      "irons_spellbooks:counterspell"),
			scroll("__default_irons_spellbooks_scroll_devour",            "Devour Scrolls",            "irons_spellbooks:devour"),
			scroll("__default_irons_spellbooks_scroll_divine_smite",      "Divine Smite Scrolls",      "irons_spellbooks:divine_smite"),
			scroll("__default_irons_spellbooks_scroll_dragon_breath",     "Dragon Breath Scrolls",     "irons_spellbooks:dragon_breath"),
			scroll("__default_irons_spellbooks_scroll_earthquake",        "Earthquake Scrolls",        "irons_spellbooks:earthquake"),
			scroll("__default_irons_spellbooks_scroll_echoing_strikes",   "Echoing Strikes Scrolls",   "irons_spellbooks:echoing_strikes"),
			scroll("__default_irons_spellbooks_scroll_eldritch_blast",    "Eldritch Blast Scrolls",    "irons_spellbooks:eldritch_blast"),
			scroll("__default_irons_spellbooks_scroll_electrocute",       "Electrocute Scrolls",       "irons_spellbooks:electrocute"),
			scroll("__default_irons_spellbooks_scroll_evasion",           "Evasion Scrolls",           "irons_spellbooks:evasion"),
			scroll("__default_irons_spellbooks_scroll_fang_strike",       "Fang Strike Scrolls",       "irons_spellbooks:fang_strike"),
			scroll("__default_irons_spellbooks_scroll_fang_ward",         "Fang Ward Scrolls",         "irons_spellbooks:fang_ward"),
			scroll("__default_irons_spellbooks_scroll_fire_arrow",        "Fire Arrow Scrolls",        "irons_spellbooks:fire_arrow"),
			scroll("__default_irons_spellbooks_scroll_fire_breath",       "Fire Breath Scrolls",       "irons_spellbooks:fire_breath"),
			scroll("__default_irons_spellbooks_scroll_fireball",          "Fireball Scrolls",          "irons_spellbooks:fireball"),
			scroll("__default_irons_spellbooks_scroll_firebolt",          "Firebolt Scrolls",          "irons_spellbooks:firebolt"),
			scroll("__default_irons_spellbooks_scroll_firecracker",       "Firecracker Scrolls",       "irons_spellbooks:firecracker"),
			scroll("__default_irons_spellbooks_scroll_firefly_swarm",     "Firefly Swarm Scrolls",     "irons_spellbooks:firefly_swarm"),
			scroll("__default_irons_spellbooks_scroll_flaming_barrage",   "Flaming Barrage Scrolls",   "irons_spellbooks:flaming_barrage"),
			scroll("__default_irons_spellbooks_scroll_flaming_strike",    "Flaming Strike Scrolls",    "irons_spellbooks:flaming_strike"),
			scroll("__default_irons_spellbooks_scroll_fortify",           "Fortify Scrolls",           "irons_spellbooks:fortify"),
			scroll("__default_irons_spellbooks_scroll_frost_step",        "Frost Step Scrolls",        "irons_spellbooks:frost_step"),
			scroll("__default_irons_spellbooks_scroll_frostbite",         "Frostbite Scrolls",         "irons_spellbooks:frostbite"),
			scroll("__default_irons_spellbooks_scroll_frostwave",         "Frostwave Scrolls",         "irons_spellbooks:frostwave"),
			scroll("__default_irons_spellbooks_scroll_gluttony",          "Gluttony Scrolls",          "irons_spellbooks:gluttony"),
			scroll("__default_irons_spellbooks_scroll_greater_heal",      "Greater Heal Scrolls",      "irons_spellbooks:greater_heal"),
			scroll("__default_irons_spellbooks_scroll_guiding_bolt",      "Guiding Bolt Scrolls",      "irons_spellbooks:guiding_bolt"),
			scroll("__default_irons_spellbooks_scroll_gust",              "Gust Scrolls",              "irons_spellbooks:gust"),
			scroll("__default_irons_spellbooks_scroll_haste",             "Haste Scrolls",             "irons_spellbooks:haste"),
			scroll("__default_irons_spellbooks_scroll_heal",              "Heal Scrolls",              "irons_spellbooks:heal"),
			scroll("__default_irons_spellbooks_scroll_healing_circle",    "Healing Circle Scrolls",    "irons_spellbooks:healing_circle"),
			scroll("__default_irons_spellbooks_scroll_heartstop",         "Heartstop Scrolls",         "irons_spellbooks:heartstop"),
			scroll("__default_irons_spellbooks_scroll_heat_surge",        "Heat Surge Scrolls",        "irons_spellbooks:heat_surge"),
			scroll("__default_irons_spellbooks_scroll_ice_block",         "Ice Block Scrolls",         "irons_spellbooks:ice_block"),
			scroll("__default_irons_spellbooks_scroll_ice_spikes",        "Ice Spikes Scrolls",        "irons_spellbooks:ice_spikes"),
			scroll("__default_irons_spellbooks_scroll_ice_tomb",          "Ice Tomb Scrolls",          "irons_spellbooks:ice_tomb"),
			scroll("__default_irons_spellbooks_scroll_icicle",            "Icicle Scrolls",            "irons_spellbooks:icicle"),
			scroll("__default_irons_spellbooks_scroll_invisibility",      "Invisibility Scrolls",      "irons_spellbooks:invisibility"),
			scroll("__default_irons_spellbooks_scroll_lightning_bolt",    "Lightning Bolt Scrolls",    "irons_spellbooks:lightning_bolt"),
			scroll("__default_irons_spellbooks_scroll_lightning_lance",   "Lightning Lance Scrolls",   "irons_spellbooks:lightning_lance"),
			scroll("__default_irons_spellbooks_scroll_lob_creeper",       "Lob Creeper Scrolls",       "irons_spellbooks:lob_creeper"),
			scroll("__default_irons_spellbooks_scroll_magic_arrow",       "Magic Arrow Scrolls",       "irons_spellbooks:magic_arrow"),
			scroll("__default_irons_spellbooks_scroll_magic_missile",     "Magic Missile Scrolls",     "irons_spellbooks:magic_missile"),
			scroll("__default_irons_spellbooks_scroll_magma_bomb",        "Magma Bomb Scrolls",        "irons_spellbooks:magma_bomb"),
			scroll("__default_irons_spellbooks_scroll_oakskin",           "Oakskin Scrolls",           "irons_spellbooks:oakskin"),
			scroll("__default_irons_spellbooks_scroll_planar_sight",      "Planar Sight Scrolls",      "irons_spellbooks:planar_sight"),
			scroll("__default_irons_spellbooks_scroll_pocket_dimension",  "Pocket Dimension Scrolls",  "irons_spellbooks:pocket_dimension"),
			scroll("__default_irons_spellbooks_scroll_poison_arrow",      "Poison Arrow Scrolls",      "irons_spellbooks:poison_arrow"),
			scroll("__default_irons_spellbooks_scroll_poison_breath",     "Poison Breath Scrolls",     "irons_spellbooks:poison_breath"),
			scroll("__default_irons_spellbooks_scroll_poison_splash",     "Poison Splash Scrolls",     "irons_spellbooks:poison_splash"),
			scroll("__default_irons_spellbooks_scroll_portal",            "Portal Scrolls",            "irons_spellbooks:portal"),
			scroll("__default_irons_spellbooks_scroll_raise_dead",        "Raise Dead Scrolls",        "irons_spellbooks:raise_dead"),
			scroll("__default_irons_spellbooks_scroll_raise_hell",        "Raise Hell Scrolls",        "irons_spellbooks:raise_hell"),
			scroll("__default_irons_spellbooks_scroll_ray_of_frost",      "Ray of Frost Scrolls",      "irons_spellbooks:ray_of_frost"),
			scroll("__default_irons_spellbooks_scroll_ray_of_siphoning",  "Ray of Siphoning Scrolls",  "irons_spellbooks:ray_of_siphoning"),
			scroll("__default_irons_spellbooks_scroll_recall",            "Recall Scrolls",            "irons_spellbooks:recall"),
			scroll("__default_irons_spellbooks_scroll_root",              "Root Scrolls",              "irons_spellbooks:root"),
			scroll("__default_irons_spellbooks_scroll_sacrifice",         "Sacrifice Scrolls",         "irons_spellbooks:sacrifice"),
			scroll("__default_irons_spellbooks_scroll_scorch",            "Scorch Scrolls",            "irons_spellbooks:scorch"),
			scroll("__default_irons_spellbooks_scroll_sculk_tentacles",   "Sculk Tentacles Scrolls",   "irons_spellbooks:sculk_tentacles"),
			scroll("__default_irons_spellbooks_scroll_shadow_slash",      "Shadow Slash Scrolls",      "irons_spellbooks:shadow_slash"),
			scroll("__default_irons_spellbooks_scroll_shield",            "Shield Scrolls",            "irons_spellbooks:shield"),
			scroll("__default_irons_spellbooks_scroll_shockwave",         "Shockwave Scrolls",         "irons_spellbooks:shockwave"),
			scroll("__default_irons_spellbooks_scroll_slow",              "Slow Scrolls",              "irons_spellbooks:slow"),
			scroll("__default_irons_spellbooks_scroll_snowball",          "Snowball Scrolls",          "irons_spellbooks:snowball"),
			scroll("__default_irons_spellbooks_scroll_sonic_boom",        "Sonic Boom Scrolls",        "irons_spellbooks:sonic_boom"),
			scroll("__default_irons_spellbooks_scroll_spectral_hammer",   "Spectral Hammer Scrolls",   "irons_spellbooks:spectral_hammer"),
			scroll("__default_irons_spellbooks_scroll_spider_aspect",     "Spider Aspect Scrolls",     "irons_spellbooks:spider_aspect"),
			scroll("__default_irons_spellbooks_scroll_starfall",          "Starfall Scrolls",          "irons_spellbooks:starfall"),
			scroll("__default_irons_spellbooks_scroll_stomp",             "Stomp Scrolls",             "irons_spellbooks:stomp"),
			scroll("__default_irons_spellbooks_scroll_summon_ender_chest","Summon Ender Chest Scrolls","irons_spellbooks:summon_ender_chest"),
			scroll("__default_irons_spellbooks_scroll_summon_horse",      "Summon Horse Scrolls",      "irons_spellbooks:summon_horse"),
			scroll("__default_irons_spellbooks_scroll_summon_polar_bear", "Summon Polar Bear Scrolls", "irons_spellbooks:summon_polar_bear"),
			scroll("__default_irons_spellbooks_scroll_summon_swords",     "Summon Swords Scrolls",     "irons_spellbooks:summon_swords"),
			scroll("__default_irons_spellbooks_scroll_summon_vex",        "Summon Vex Scrolls",        "irons_spellbooks:summon_vex"),
			scroll("__default_irons_spellbooks_scroll_sunbeam",           "Sunbeam Scrolls",           "irons_spellbooks:sunbeam"),
			scroll("__default_irons_spellbooks_scroll_telekinesis",       "Telekinesis Scrolls",       "irons_spellbooks:telekinesis"),
			scroll("__default_irons_spellbooks_scroll_teleport",          "Teleport Scrolls",          "irons_spellbooks:teleport"),
			scroll("__default_irons_spellbooks_scroll_throw",             "Throw Scrolls",             "irons_spellbooks:throw"),
			scroll("__default_irons_spellbooks_scroll_thunder_step",      "Thunder Step Scrolls",      "irons_spellbooks:thunder_step"),
			scroll("__default_irons_spellbooks_scroll_thunderstorm",      "Thunderstorm Scrolls",      "irons_spellbooks:thunderstorm"),
			scroll("__default_irons_spellbooks_scroll_touch_dig",         "Touch Dig Scrolls",         "irons_spellbooks:touch_dig"),
			scroll("__default_irons_spellbooks_scroll_volt_strike",       "Volt Strike Scrolls",       "irons_spellbooks:volt_strike"),
			scroll("__default_irons_spellbooks_scroll_wall_of_fire",      "Wall of Fire Scrolls",      "irons_spellbooks:wall_of_fire"),
			scroll("__default_irons_spellbooks_scroll_wisp",              "Wisp Scrolls",              "irons_spellbooks:wisp"),
			scroll("__default_irons_spellbooks_scroll_wither_skull",      "Wither Skull Scrolls",      "irons_spellbooks:wither_skull"),
			scroll("__default_irons_spellbooks_scroll_wololo",            "Wololo Scrolls",            "irons_spellbooks:wololo")
		);
	}

	private static GroupDefinition scroll(String groupId, String fallbackName, String spellId) {
		return group(
			groupId,
			fallbackName,
			itemWithComponentPath(SCROLL_ITEM_ID, SPELL_COMPONENT, SPELL_PATH, spellId)
		);
	}
}
