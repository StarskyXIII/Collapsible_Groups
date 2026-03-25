package com.starskyxiii.collapsible_groups.defaults.integration;

import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.*;

import java.util.List;

/**
 * Built-in groups for Rechiseled block variants, one group per tag.
 * Automatically skipped if Rechiseled is not installed.
 */
public final class Rechiseled implements DefaultGroupProvider {

	@Override
	public int priority() {
		return 600;
	}

	private static GroupFilter rechiseledTag(String tag) {
		return Filters.all(Filters.itemTag(tag), Filters.itemNamespace("rechiseled"));
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!Services.CONFIG.shouldLoadRechiseled()) return List.of();
		return List.of(
            group("__default_rechiseled_acacia_planks", "Rechiseled: Acacia Planks", rechiseledTag("rechiseled:acacia_planks")),
            group("__default_rechiseled_amethyst_block", "Rechiseled: Amethyst Block", rechiseledTag("rechiseled:amethyst_block")),   
            group("__default_rechiseled_andesite", "Rechiseled: Andesite", rechiseledTag("rechiseled:andesite")),
            group("__default_rechiseled_bamboo_planks", "Rechiseled: Bamboo Planks", rechiseledTag("rechiseled:bamboo_planks")),
            group("__default_rechiseled_basalt", "Rechiseled: Basalt", rechiseledTag("rechiseled:basalt")),
            group("__default_rechiseled_birch_planks", "Rechiseled: Birch Planks", rechiseledTag("rechiseled:birch_planks")),
            group("__default_rechiseled_blackstone", "Rechiseled: Blackstone", rechiseledTag("rechiseled:blackstone")),
            group("__default_rechiseled_blue_ice", "Rechiseled: Blue Ice", rechiseledTag("rechiseled:blue_ice")),
            group("__default_rechiseled_bone_block", "Rechiseled: Bone Block", rechiseledTag("rechiseled:bone_block")),
            group("__default_rechiseled_cherry_planks", "Rechiseled: Cherry Planks", rechiseledTag("rechiseled:cherry_planks")),
            group("__default_rechiseled_coal_block", "Rechiseled: Coal Block", rechiseledTag("rechiseled:coal_block")),
            group("__default_rechiseled_cobbled_deepslate", "Rechiseled: Cobbled Deepslate", rechiseledTag("rechiseled:cobbled_deepslate")),
            group("__default_rechiseled_cobblestone", "Rechiseled: Cobblestone", rechiseledTag("rechiseled:cobblestone")),
            group("__default_rechiseled_copper_block", "Rechiseled: Copper Block", rechiseledTag("rechiseled:copper_block")),
            group("__default_rechiseled_crimson_planks", "Rechiseled: Crimson Planks", rechiseledTag("rechiseled:crimson_planks")),
            group("__default_rechiseled_dark_oak_planks", "Rechiseled: Dark Oak Planks", rechiseledTag("rechiseled:dark_oak_planks")),
            group("__default_rechiseled_dark_prismarine", "Rechiseled: Dark Prismarine", rechiseledTag("rechiseled:dark_prismarine")),
            group("__default_rechiseled_diamond_block", "Rechiseled: Diamond Block", rechiseledTag("rechiseled:diamond_block")),
            group("__default_rechiseled_diorite", "Rechiseled: Diorite", rechiseledTag("rechiseled:diorite")),
            group("__default_rechiseled_dirt", "Rechiseled: Dirt", rechiseledTag("rechiseled:dirt")),
            group("__default_rechiseled_emerald_block", "Rechiseled: Emerald Block", rechiseledTag("rechiseled:emerald_block")),
            group("__default_rechiseled_end_stone", "Rechiseled: End Stone", rechiseledTag("rechiseled:end_stone")),
            group("__default_rechiseled_glowstone", "Rechiseled: Glowstone", rechiseledTag("rechiseled:glowstone")),
            group("__default_rechiseled_gold_block", "Rechiseled: Gold Block", rechiseledTag("rechiseled:gold_block")),
            group("__default_rechiseled_granite", "Rechiseled: Granite", rechiseledTag("rechiseled:granite")),
            group("__default_rechiseled_iron_block", "Rechiseled: Iron Block", rechiseledTag("rechiseled:iron_block")),
            group("__default_rechiseled_jungle_planks", "Rechiseled: Jungle Planks", rechiseledTag("rechiseled:jungle_planks")),
            group("__default_rechiseled_lapis_block", "Rechiseled: Lapis Block", rechiseledTag("rechiseled:lapis_block")),
            group("__default_rechiseled_mangrove_planks", "Rechiseled: Mangrove Planks", rechiseledTag("rechiseled:mangrove_planks")),
            group("__default_rechiseled_nether_bricks", "Rechiseled: Nether Bricks", rechiseledTag("rechiseled:nether_bricks")),
            group("__default_rechiseled_netherite_block", "Rechiseled: Netherite Block", rechiseledTag("rechiseled:netherite_block")),
            group("__default_rechiseled_netherrack", "Rechiseled: Netherrack", rechiseledTag("rechiseled:netherrack")),
            group("__default_rechiseled_oak_planks", "Rechiseled: Oak Planks", rechiseledTag("rechiseled:oak_planks")),
            group("__default_rechiseled_obsidian", "Rechiseled: Obsidian", rechiseledTag("rechiseled:obsidian")),
            group("__default_rechiseled_prismarine_bricks", "Rechiseled: Prismarine Bricks", rechiseledTag("rechiseled:prismarine_bricks")),
            group("__default_rechiseled_purpur_block", "Rechiseled: Purpur Block", rechiseledTag("rechiseled:purpur_block")),
            group("__default_rechiseled_quartz_block", "Rechiseled: Quartz Block", rechiseledTag("rechiseled:quartz_block")),
            group("__default_rechiseled_red_nether_bricks", "Rechiseled: Red Nether Bricks", rechiseledTag("rechiseled:red_nether_bricks")),
            group("__default_rechiseled_red_sandstone", "Rechiseled: Red Sandstone", rechiseledTag("rechiseled:red_sandstone")),
            group("__default_rechiseled_redstone_block", "Rechiseled: Redstone Block", rechiseledTag("rechiseled:redstone_block")),
            group("__default_rechiseled_sandstone", "Rechiseled: Sandstone", rechiseledTag("rechiseled:sandstone")),
            group("__default_rechiseled_spruce_planks", "Rechiseled: Spruce Planks", rechiseledTag("rechiseled:spruce_planks")),
            group("__default_rechiseled_stone", "Rechiseled: Stone", rechiseledTag("rechiseled:stone")),
            group("__default_rechiseled_warped_planks", "Rechiseled: Warped Planks", rechiseledTag("rechiseled:warped_planks"))
		);
	}
}
