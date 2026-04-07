package com.starskyxiii.collapsible_groups.defaults.integration;

import com.starskyxiii.collapsible_groups.config.NeoForgeConfig;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.*;

import java.util.List;

/**
 * Built-in groups for Chisel block variants, one group per carving tag.
 * Automatically skipped if Chisel is not installed.
 */
public final class Chisel implements DefaultGroupProvider {

	@Override
	public int priority() {
		return 700;
	}

	private static GroupFilter chiselTag(String tag) {
		return Filters.all(Filters.itemTag(tag), Filters.itemNamespace("chisel"));
	}

	private static GroupFilter chiselCarving(String name) {
		return chiselTag("chisel:carving/" + name);
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!NeoForgeConfig.shouldLoadChisel()) return List.of();
		return List.of(
			// Stone & Rock
			group("__default_chisel_andesite",          "Chisel: Andesite",                  chiselCarving("andesite")),
			group("__default_chisel_basalt",            "Chisel: Basalt",                    chiselCarving("basalt")),
			group("__default_chisel_bricks",            "Chisel: Bricks",                    chiselCarving("bricks")),
			group("__default_chisel_cobblestone",       "Chisel: Cobblestone",               chiselCarving("cobblestone")),
			group("__default_chisel_mossy_cobblestone", "Chisel: Mossy Cobblestone",         chiselCarving("mossy_cobblestone")),
			group("__default_chisel_diorite",           "Chisel: Diorite",                   chiselCarving("diorite")),
			group("__default_chisel_granite",           "Chisel: Granite",                   chiselCarving("granite")),
			group("__default_chisel_stone_bricks",      "Chisel: Stone Bricks",              chiselCarving("stone_bricks")),
			group("__default_chisel_end_stone",         "Chisel: End Stone",                 chiselCarving("end_stone")),
			group("__default_chisel_obsidian",          "Chisel: Obsidian",                  chiselCarving("obsidian")),
			group("__default_chisel_prismarine",        "Chisel: Prismarine",                chiselCarving("prismarine")),
			group("__default_chisel_purpur",            "Chisel: Purpur",                    chiselCarving("purpur")),
			group("__default_chisel_netherrack",        "Chisel: Netherrack",                chiselCarving("netherrack")),
			group("__default_chisel_netherbrick",       "Chisel: Nether Brick",              chiselCarving("netherbrick")),
			// Sandstone
			group("__default_chisel_sandstone",             "Chisel: Sandstone",             chiselCarving("sandstone")),
			group("__default_chisel_sandstone_scribbles",   "Chisel: Sandstone Scribbles",   chiselCarving("sandstone_scribbles")),
			group("__default_chisel_red_sandstone",         "Chisel: Red Sandstone",         chiselCarving("red_sandstone")),
			group("__default_chisel_sandstonered_scribbles","Chisel: Red Sandstone Scribbles",chiselCarving("sandstonered_scribbles")),
			// Dirt & Terrain
			group("__default_chisel_dirt",        "Chisel: Dirt",       chiselCarving("dirt")),
			group("__default_chisel_ice",         "Chisel: Ice",        chiselCarving("ice")),
			group("__default_chisel_icepillar",   "Chisel: Ice Pillar", chiselCarving("icepillar")),
			group("__default_chisel_glowstone",   "Chisel: Glowstone",  chiselCarving("glowstone")),
			group("__default_chisel_quartz",      "Chisel: Quartz",     chiselCarving("quartz")),
			// Ore Blocks
			group("__default_chisel_coal",      "Chisel: Coal",      chiselCarving("coal")),
			group("__default_chisel_charcoal",  "Chisel: Charcoal",  chiselCarving("charcoal")),
			group("__default_chisel_diamond",   "Chisel: Diamond",   chiselCarving("diamond")),
			group("__default_chisel_emerald",   "Chisel: Emerald",   chiselCarving("emerald")),
			group("__default_chisel_gold",      "Chisel: Gold",      chiselCarving("gold")),
			group("__default_chisel_iron",      "Chisel: Iron",      chiselCarving("iron")),
			group("__default_chisel_lapis",     "Chisel: Lapis",     chiselCarving("lapis")),
			group("__default_chisel_redstone",  "Chisel: Redstone",  chiselCarving("redstone")),
			// Planks
			group("__default_chisel_planks_acacia",   "Chisel: Acacia Planks",   chiselCarving("planks_acacia")),
			group("__default_chisel_planks_birch",    "Chisel: Birch Planks",    chiselCarving("planks_birch")),
			group("__default_chisel_planks_crimson",  "Chisel: Crimson Planks",  chiselCarving("planks_crimson")),
			group("__default_chisel_planks_dark_oak", "Chisel: Dark Oak Planks", chiselCarving("planks_dark_oak")),
			group("__default_chisel_planks_jungle",   "Chisel: Jungle Planks",   chiselCarving("planks_jungle")),
			group("__default_chisel_planks_oak",      "Chisel: Oak Planks",      chiselCarving("planks_oak")),
			group("__default_chisel_planks_spruce",   "Chisel: Spruce Planks",   chiselCarving("planks_spruce")),
			// Bookshelves
			group("__default_chisel_bookshelf_acacia",   "Chisel: Acacia Bookshelf",   chiselCarving("bookshelf_acacia")),
			group("__default_chisel_bookshelf_bamboo",   "Chisel: Bamboo Bookshelf",   chiselCarving("bookshelf_bamboo")),
			group("__default_chisel_bookshelf_birch",    "Chisel: Birch Bookshelf",    chiselCarving("bookshelf_birch")),
			group("__default_chisel_bookshelf_cherry",   "Chisel: Cherry Bookshelf",   chiselCarving("bookshelf_cherry")),
			group("__default_chisel_bookshelf_crimson",  "Chisel: Crimson Bookshelf",  chiselCarving("bookshelf_crimson")),
			group("__default_chisel_bookshelf_dark_oak", "Chisel: Dark Oak Bookshelf", chiselCarving("bookshelf_dark_oak")),
			group("__default_chisel_bookshelf_jungle",   "Chisel: Jungle Bookshelf",   chiselCarving("bookshelf_jungle")),
			group("__default_chisel_bookshelf_mangrove", "Chisel: Mangrove Bookshelf", chiselCarving("bookshelf_mangrove")),
			group("__default_chisel_bookshelf_oak",      "Chisel: Oak Bookshelf",      chiselCarving("bookshelf_oak")),
			group("__default_chisel_bookshelf_spruce",   "Chisel: Spruce Bookshelf",   chiselCarving("bookshelf_spruce")),
			group("__default_chisel_bookshelf_warped",   "Chisel: Warped Bookshelf",   chiselCarving("bookshelf_warped")),
			// Carpet
			group("__default_chisel_carpet_black",      "Chisel: Black Carpet",      chiselCarving("carpet_black")),
			group("__default_chisel_carpet_blue",       "Chisel: Blue Carpet",       chiselCarving("carpet_blue")),
			group("__default_chisel_carpet_brown",      "Chisel: Brown Carpet",      chiselCarving("carpet_brown")),
			group("__default_chisel_carpet_cyan",       "Chisel: Cyan Carpet",       chiselCarving("carpet_cyan")),
			group("__default_chisel_carpet_gray",       "Chisel: Gray Carpet",       chiselCarving("carpet_gray")),
			group("__default_chisel_carpet_green",      "Chisel: Green Carpet",      chiselCarving("carpet_green")),
			group("__default_chisel_carpet_light_blue", "Chisel: Light Blue Carpet", chiselCarving("carpet_light_blue")),
			group("__default_chisel_carpet_light_gray", "Chisel: Light Gray Carpet", chiselCarving("carpet_light_gray")),
			group("__default_chisel_carpet_lime",       "Chisel: Lime Carpet",       chiselCarving("carpet_lime")),
			group("__default_chisel_carpet_magenta",    "Chisel: Magenta Carpet",    chiselCarving("carpet_magenta")),
			group("__default_chisel_carpet_orange",     "Chisel: Orange Carpet",     chiselCarving("carpet_orange")),
			group("__default_chisel_carpet_pink",       "Chisel: Pink Carpet",       chiselCarving("carpet_pink")),
			group("__default_chisel_carpet_purple",     "Chisel: Purple Carpet",     chiselCarving("carpet_purple")),
			group("__default_chisel_carpet_red",        "Chisel: Red Carpet",        chiselCarving("carpet_red")),
			group("__default_chisel_carpet_white",      "Chisel: White Carpet",      chiselCarving("carpet_white")),
			group("__default_chisel_carpet_yellow",     "Chisel: Yellow Carpet",     chiselCarving("carpet_yellow")),
			// Concrete
			group("__default_chisel_concrete_black",      "Chisel: Black Concrete",      chiselCarving("concrete_black")),
			group("__default_chisel_concrete_blue",       "Chisel: Blue Concrete",       chiselCarving("concrete_blue")),
			group("__default_chisel_concrete_brown",      "Chisel: Brown Concrete",      chiselCarving("concrete_brown")),
			group("__default_chisel_concrete_cyan",       "Chisel: Cyan Concrete",       chiselCarving("concrete_cyan")),
			group("__default_chisel_concrete_gray",       "Chisel: Gray Concrete",       chiselCarving("concrete_gray")),
			group("__default_chisel_concrete_green",      "Chisel: Green Concrete",      chiselCarving("concrete_green")),
			group("__default_chisel_concrete_light_blue", "Chisel: Light Blue Concrete", chiselCarving("concrete_light_blue")),
			group("__default_chisel_concrete_light_gray", "Chisel: Light Gray Concrete", chiselCarving("concrete_light_gray")),
			group("__default_chisel_concrete_lime",       "Chisel: Lime Concrete",       chiselCarving("concrete_lime")),
			group("__default_chisel_concrete_magenta",    "Chisel: Magenta Concrete",    chiselCarving("concrete_magenta")),
			group("__default_chisel_concrete_orange",     "Chisel: Orange Concrete",     chiselCarving("concrete_orange")),
			group("__default_chisel_concrete_pink",       "Chisel: Pink Concrete",       chiselCarving("concrete_pink")),
			group("__default_chisel_concrete_purple",     "Chisel: Purple Concrete",     chiselCarving("concrete_purple")),
			group("__default_chisel_concrete_red",        "Chisel: Red Concrete",        chiselCarving("concrete_red")),
			group("__default_chisel_concrete_white",      "Chisel: White Concrete",      chiselCarving("concrete_white")),
			group("__default_chisel_concrete_yellow",     "Chisel: Yellow Concrete",     chiselCarving("concrete_yellow")),
			// Glass
			group("__default_chisel_glass", "Chisel: Glass", chiselCarving("glass")),
			group("__default_chisel_glass_stained_black",      "Chisel: Black Stained Glass",      chiselCarving("glass_stained_black")),
			group("__default_chisel_glass_stained_blue",       "Chisel: Blue Stained Glass",       chiselCarving("glass_stained_blue")),
			group("__default_chisel_glass_stained_brown",      "Chisel: Brown Stained Glass",      chiselCarving("glass_stained_brown")),
			group("__default_chisel_glass_stained_cyan",       "Chisel: Cyan Stained Glass",       chiselCarving("glass_stained_cyan")),
			group("__default_chisel_glass_stained_gray",       "Chisel: Gray Stained Glass",       chiselCarving("glass_stained_gray")),
			group("__default_chisel_glass_stained_green",      "Chisel: Green Stained Glass",      chiselCarving("glass_stained_green")),
			group("__default_chisel_glass_stained_light_blue", "Chisel: Light Blue Stained Glass", chiselCarving("glass_stained_light_blue")),
			group("__default_chisel_glass_stained_light_gray", "Chisel: Light Gray Stained Glass", chiselCarving("glass_stained_light_gray")),
			group("__default_chisel_glass_stained_lime",       "Chisel: Lime Stained Glass",       chiselCarving("glass_stained_lime")),
			group("__default_chisel_glass_stained_magenta",    "Chisel: Magenta Stained Glass",    chiselCarving("glass_stained_magenta")),
			group("__default_chisel_glass_stained_orange",     "Chisel: Orange Stained Glass",     chiselCarving("glass_stained_orange")),
			group("__default_chisel_glass_stained_pink",       "Chisel: Pink Stained Glass",       chiselCarving("glass_stained_pink")),
			group("__default_chisel_glass_stained_purple",     "Chisel: Purple Stained Glass",     chiselCarving("glass_stained_purple")),
			group("__default_chisel_glass_stained_red",        "Chisel: Red Stained Glass",        chiselCarving("glass_stained_red")),
			group("__default_chisel_glass_stained_white",      "Chisel: White Stained Glass",      chiselCarving("glass_stained_white")),
			group("__default_chisel_glass_stained_yellow",     "Chisel: Yellow Stained Glass",     chiselCarving("glass_stained_yellow")),
			// Dyed Glass
			group("__default_chisel_glassdyed_black",      "Chisel: Black Dyed Glass",      chiselCarving("glassdyed_black")),
			group("__default_chisel_glassdyed_blue",       "Chisel: Blue Dyed Glass",       chiselCarving("glassdyed_blue")),
			group("__default_chisel_glassdyed_brown",      "Chisel: Brown Dyed Glass",      chiselCarving("glassdyed_brown")),
			group("__default_chisel_glassdyed_cyan",       "Chisel: Cyan Dyed Glass",       chiselCarving("glassdyed_cyan")),
			group("__default_chisel_glassdyed_gray",       "Chisel: Gray Dyed Glass",       chiselCarving("glassdyed_gray")),
			group("__default_chisel_glassdyed_green",      "Chisel: Green Dyed Glass",      chiselCarving("glassdyed_green")),
			group("__default_chisel_glassdyed_light_blue", "Chisel: Light Blue Dyed Glass", chiselCarving("glassdyed_light_blue")),
			group("__default_chisel_glassdyed_light_gray", "Chisel: Light Gray Dyed Glass", chiselCarving("glassdyed_light_gray")),
			group("__default_chisel_glassdyed_lime",       "Chisel: Lime Dyed Glass",       chiselCarving("glassdyed_lime")),
			group("__default_chisel_glassdyed_magenta",    "Chisel: Magenta Dyed Glass",    chiselCarving("glassdyed_magenta")),
			group("__default_chisel_glassdyed_orange",     "Chisel: Orange Dyed Glass",     chiselCarving("glassdyed_orange")),
			group("__default_chisel_glassdyed_pink",       "Chisel: Pink Dyed Glass",       chiselCarving("glassdyed_pink")),
			group("__default_chisel_glassdyed_purple",     "Chisel: Purple Dyed Glass",     chiselCarving("glassdyed_purple")),
			group("__default_chisel_glassdyed_red",        "Chisel: Red Dyed Glass",        chiselCarving("glassdyed_red")),
			group("__default_chisel_glassdyed_white",      "Chisel: White Dyed Glass",      chiselCarving("glassdyed_white")),
			group("__default_chisel_glassdyed_yellow",     "Chisel: Yellow Dyed Glass",     chiselCarving("glassdyed_yellow")),
			// Dyed Glass Pane
			group("__default_chisel_glasspanedyed_black",      "Chisel: Black Dyed Glass Pane",      chiselCarving("glasspanedyed_black")),
			group("__default_chisel_glasspanedyed_blue",       "Chisel: Blue Dyed Glass Pane",       chiselCarving("glasspanedyed_blue")),
			group("__default_chisel_glasspanedyed_brown",      "Chisel: Brown Dyed Glass Pane",      chiselCarving("glasspanedyed_brown")),
			group("__default_chisel_glasspanedyed_cyan",       "Chisel: Cyan Dyed Glass Pane",       chiselCarving("glasspanedyed_cyan")),
			group("__default_chisel_glasspanedyed_gray",       "Chisel: Gray Dyed Glass Pane",       chiselCarving("glasspanedyed_gray")),
			group("__default_chisel_glasspanedyed_green",      "Chisel: Green Dyed Glass Pane",      chiselCarving("glasspanedyed_green")),
			group("__default_chisel_glasspanedyed_light_blue", "Chisel: Light Blue Dyed Glass Pane", chiselCarving("glasspanedyed_light_blue")),
			group("__default_chisel_glasspanedyed_light_gray", "Chisel: Light Gray Dyed Glass Pane", chiselCarving("glasspanedyed_light_gray")),
			group("__default_chisel_glasspanedyed_lime",       "Chisel: Lime Dyed Glass Pane",       chiselCarving("glasspanedyed_lime")),
			group("__default_chisel_glasspanedyed_magenta",    "Chisel: Magenta Dyed Glass Pane",    chiselCarving("glasspanedyed_magenta")),
			group("__default_chisel_glasspanedyed_orange",     "Chisel: Orange Dyed Glass Pane",     chiselCarving("glasspanedyed_orange")),
			group("__default_chisel_glasspanedyed_pink",       "Chisel: Pink Dyed Glass Pane",       chiselCarving("glasspanedyed_pink")),
			group("__default_chisel_glasspanedyed_purple",     "Chisel: Purple Dyed Glass Pane",     chiselCarving("glasspanedyed_purple")),
			group("__default_chisel_glasspanedyed_red",        "Chisel: Red Dyed Glass Pane",        chiselCarving("glasspanedyed_red")),
			group("__default_chisel_glasspanedyed_white",      "Chisel: White Dyed Glass Pane",      chiselCarving("glasspanedyed_white")),
			group("__default_chisel_glasspanedyed_yellow",     "Chisel: Yellow Dyed Glass Pane",     chiselCarving("glasspanedyed_yellow")),
			// Hexplating
			group("__default_chisel_hexplating_black",      "Chisel: Black Hexplating",      chiselCarving("hexplating_black")),
			group("__default_chisel_hexplating_blue",       "Chisel: Blue Hexplating",       chiselCarving("hexplating_blue")),
			group("__default_chisel_hexplating_brown",      "Chisel: Brown Hexplating",      chiselCarving("hexplating_brown")),
			group("__default_chisel_hexplating_cyan",       "Chisel: Cyan Hexplating",       chiselCarving("hexplating_cyan")),
			group("__default_chisel_hexplating_gray",       "Chisel: Gray Hexplating",       chiselCarving("hexplating_gray")),
			group("__default_chisel_hexplating_green",      "Chisel: Green Hexplating",      chiselCarving("hexplating_green")),
			group("__default_chisel_hexplating_light_blue", "Chisel: Light Blue Hexplating", chiselCarving("hexplating_light_blue")),
			group("__default_chisel_hexplating_light_gray", "Chisel: Light Gray Hexplating", chiselCarving("hexplating_light_gray")),
			group("__default_chisel_hexplating_lime",       "Chisel: Lime Hexplating",       chiselCarving("hexplating_lime")),
			group("__default_chisel_hexplating_magenta",    "Chisel: Magenta Hexplating",    chiselCarving("hexplating_magenta")),
			group("__default_chisel_hexplating_orange",     "Chisel: Orange Hexplating",     chiselCarving("hexplating_orange")),
			group("__default_chisel_hexplating_pink",       "Chisel: Pink Hexplating",       chiselCarving("hexplating_pink")),
			group("__default_chisel_hexplating_purple",     "Chisel: Purple Hexplating",     chiselCarving("hexplating_purple")),
			group("__default_chisel_hexplating_red",        "Chisel: Red Hexplating",        chiselCarving("hexplating_red")),
			group("__default_chisel_hexplating_white",      "Chisel: White Hexplating",      chiselCarving("hexplating_white")),
			group("__default_chisel_hexplating_yellow",     "Chisel: Yellow Hexplating",     chiselCarving("hexplating_yellow")),
			// Wool
			group("__default_chisel_wool_black",      "Chisel: Black Wool",      chiselCarving("wool_black")),
			group("__default_chisel_wool_blue",       "Chisel: Blue Wool",       chiselCarving("wool_blue")),
			group("__default_chisel_wool_brown",      "Chisel: Brown Wool",      chiselCarving("wool_brown")),
			group("__default_chisel_wool_cyan",       "Chisel: Cyan Wool",       chiselCarving("wool_cyan")),
			group("__default_chisel_wool_gray",       "Chisel: Gray Wool",       chiselCarving("wool_gray")),
			group("__default_chisel_wool_green",      "Chisel: Green Wool",      chiselCarving("wool_green")),
			group("__default_chisel_wool_light_blue", "Chisel: Light Blue Wool", chiselCarving("wool_light_blue")),
			group("__default_chisel_wool_light_gray", "Chisel: Light Gray Wool", chiselCarving("wool_light_gray")),
			group("__default_chisel_wool_lime",       "Chisel: Lime Wool",       chiselCarving("wool_lime")),
			group("__default_chisel_wool_magenta",    "Chisel: Magenta Wool",    chiselCarving("wool_magenta")),
			group("__default_chisel_wool_orange",     "Chisel: Orange Wool",     chiselCarving("wool_orange")),
			group("__default_chisel_wool_pink",       "Chisel: Pink Wool",       chiselCarving("wool_pink")),
			group("__default_chisel_wool_purple",     "Chisel: Purple Wool",     chiselCarving("wool_purple")),
			group("__default_chisel_wool_red",        "Chisel: Red Wool",        chiselCarving("wool_red")),
			group("__default_chisel_wool_white",      "Chisel: White Wool",      chiselCarving("wool_white")),
			group("__default_chisel_wool_yellow",     "Chisel: Yellow Wool",     chiselCarving("wool_yellow")),
			// Mod-exclusive Materials
			group("__default_chisel_antiblock",   "Chisel: Antiblock",   chiselCarving("antiblock")),
			group("__default_chisel_brownstone",  "Chisel: Brownstone",  chiselCarving("brownstone")),
			group("__default_chisel_cloud",       "Chisel: Cloud",       chiselCarving("cloud")),
			group("__default_chisel_cubits",      "Chisel: Cubits",      chiselCarving("cubits")),
			group("__default_chisel_diabase",     "Chisel: Diabase",     chiselCarving("diabase")),
			group("__default_chisel_factory",     "Chisel: Factory",     chiselCarving("factory")),
			group("__default_chisel_fantasy",     "Chisel: Fantasy",     chiselCarving("fantasy")),
			group("__default_chisel_fantasy2",    "Chisel: Fantasy 2",   chiselCarving("fantasy2")),
			group("__default_chisel_futura",      "Chisel: Futura",      chiselCarving("futura")),
			group("__default_chisel_holystone",   "Chisel: Holystone",   chiselCarving("holystone")),
			group("__default_chisel_ironpane",    "Chisel: Iron Pane",   chiselCarving("ironpane")),
			group("__default_chisel_laboratory",  "Chisel: Laboratory",  chiselCarving("laboratory")),
			group("__default_chisel_lavastone",   "Chisel: Lavastone",   chiselCarving("lavastone")),
			group("__default_chisel_limestone",   "Chisel: Limestone",   chiselCarving("limestone")),
			group("__default_chisel_marble",      "Chisel: Marble",      chiselCarving("marble")),
			group("__default_chisel_marblepillar","Chisel: Marble Pillar",chiselCarving("marblepillar")),
			group("__default_chisel_paper",       "Chisel: Paper",       chiselCarving("paper")),
			group("__default_chisel_technical",   "Chisel: Technical",
				Filters.all(
					Filters.any(
						Filters.itemTag("chisel:carving/technical"),
						Filters.itemTag("chisel:carving/technical_transparent")
					),
					Filters.itemNamespace("chisel")
				)),
			group("__default_chisel_temple",      "Chisel: Temple",      chiselCarving("temple")),
			group("__default_chisel_templemossy", "Chisel: Mossy Temple",chiselCarving("templemossy")),
			group("__default_chisel_terracotta",  "Chisel: Terracotta",  chiselCarving("terracotta")),
			group("__default_chisel_tyrian",      "Chisel: Tyrian",      chiselCarving("tyrian")),
			group("__default_chisel_valentines",  "Chisel: Valentines",  chiselCarving("valentines")),
			group("__default_chisel_voidstone",         "Chisel: Voidstone",              chiselCarving("voidstone")),
			group("__default_chisel_voidstonerunic",    "Chisel: Voidstone Runic",        chiselCarving("voidstonerunic")),
			group("__default_chisel_voidstonerunic_anim","Chisel: Voidstone Runic (Anim)",chiselCarving("voidstonerunic_anim")),
			group("__default_chisel_warning",    "Chisel: Warning",    chiselCarving("warning")),
			group("__default_chisel_waterstone", "Chisel: Waterstone", chiselCarving("waterstone")),
			// Compat Metals
			group("__default_chisel_metals_aluminum", "Chisel: Aluminum Blocks",  chiselCarving("metals_aluminum")),
			group("__default_chisel_metals_bronze",   "Chisel: Bronze Blocks",    chiselCarving("metals_bronze")),
			group("__default_chisel_metals_cobalt",   "Chisel: Cobalt Blocks",    chiselCarving("metals_cobalt")),
			group("__default_chisel_metals_copper",   "Chisel: Copper Blocks",    chiselCarving("metals_copper")),
			group("__default_chisel_metals_electrum", "Chisel: Electrum Blocks",  chiselCarving("metals_electrum")),
			group("__default_chisel_metals_invar",    "Chisel: Invar Blocks",     chiselCarving("metals_invar")),
			group("__default_chisel_metals_lead",     "Chisel: Lead Blocks",      chiselCarving("metals_lead")),
			group("__default_chisel_metals_nickel",   "Chisel: Nickel Blocks",    chiselCarving("metals_nickel")),
			group("__default_chisel_metals_platinum", "Chisel: Platinum Blocks",  chiselCarving("metals_platinum")),
			group("__default_chisel_metals_silver",   "Chisel: Silver Blocks",    chiselCarving("metals_silver")),
			group("__default_chisel_metals_steel",    "Chisel: Steel Blocks",     chiselCarving("metals_steel")),
			group("__default_chisel_metals_tin",      "Chisel: Tin Blocks",       chiselCarving("metals_tin")),
			group("__default_chisel_metals_uranium",  "Chisel: Uranium Blocks",   chiselCarving("metals_uranium"))
		);
	}
}
