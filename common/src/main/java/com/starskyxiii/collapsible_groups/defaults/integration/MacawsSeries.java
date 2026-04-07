package com.starskyxiii.collapsible_groups.defaults.integration;

import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import com.starskyxiii.collapsible_groups.platform.Services;

import java.util.ArrayList;
import java.util.List;

import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.group;

/**
 * Built-in groups for Macaw's series block-tag categories.
 * Automatically skipped when none of the supported Macaw's mods are installed.
 */
public final class MacawsSeries implements DefaultGroupProvider {

	private record ModuleSpec(String namespace, String idPrefix, String displayPrefix, String[] tags) {}

	// Windows
	private static final ModuleSpec WINDOWS = new ModuleSpec(
		"mcwwindows",
		"__default_mcw_windows_",
		"Macaw's Windows: ",
		new String[]{
			"arrow_slit",
			"blinds",
			"curtain_rods",
			"curtains",
			"gothic",
			"mosaic_glass",
			"mosaic_glass_pane",
			"parapets",
			"shutters",
			"windows",
			"windows_four",
			"windows_two",
		}
	);

	// Bridges
	private static final ModuleSpec BRIDGES = new ModuleSpec(
		"mcwbridges",
		"__default_mcw_bridges_",
		"Macaw's Bridges: ",
		new String[]{
			"bamboo_bridges",
			"bamboo_piers",
			"bamboo_stairs",
			"glass_bridges",
			"log_bridges",
			"log_stairs",
			"metal_bridges",
			"rail_bridges",
			"rope_bridges",
			"rope_stairs",
			"special_bridges",
			"stone_bridges",
			"stone_piers",
			"stone_stairs",
			"wooden_piers",
		}
	);

	// Doors
	private static final ModuleSpec DOORS = new ModuleSpec(
		"mcwdoors",
		"__default_mcw_doors_",
		"Macaw's Doors: ",
		new String[]{
			"bamboo_doors",
			"bark_glass_doors",
			"barn_doors",
			"barn_glass_doors",
			"beach_doors",
			"classic_doors",
			"cottage_doors",
			"four_panel_doors",
			"garage_doors",
			"glass_doors",
			"metal_doors",
			"modern_doors",
			"mystic_doors",
			"nether_doors",
			"paper_doors",
			"portcullis",
			"shoji_doors",
			"shoji_whole_doors",
			"special_doors",
			"stable_doors",
			"stable_head_doors",
			"swamp_doors",
			"tropical_doors",
			"waffle_doors",
			"western_doors",
			"whispering_doors",
		}
	);

	// Fences
	private static final ModuleSpec FENCES = new ModuleSpec(
		"mcwfences",
		"__default_mcw_fences_",
		"Macaw's Fences: ",
		new String[]{
			"cheval_de_frise",
			"curved_double_gates",
			"grass_topped_walls",
			"hedges",
			"highley_gates",
			"horse_fences",
			"metal_double_gates",
			"metal_fences",
			"modern_walls",
			"picket_fences",
			"pillar_walls",
			"pyramid_gates",
			"railing_gates",
			"railing_walls",
			"stockade_fences",
			"wired_fences",
		}
	);

	// Furnitures
	private static final ModuleSpec FURNITURES = new ModuleSpec(
		"mcwfurnitures",
		"__default_mcw_furnitures_",
		"Macaw's Furnitures: ",
		new String[]{
			"bookshelf",
			"bookshelf_cupboard",
			"bookshelf_drawer",
			"cabinet",
			"chair",
			"chaise",
			"coffee_table",
			"couch",
			"counter",
			"covered_desk",
			"cupboard_counter",
			"desk",
			"double_drawer",
			"double_drawer_counter",
			"double_wardrobe",
			"drawer",
			"drawer_counter",
			"end_table",
			"glass_table",
			"kitchen_sink",
			"large_drawer",
			"lower_bookshelf_drawer",
			"lower_triple_drawer",
			"modern_chair",
			"modern_desk",
			"modern_wardrobe",
			"stool_chair",
			"striped_chair",
			"table",
			"triple_drawer",
			"wadrobe",
		}
	);

	// Lights
	private static final ModuleSpec LIGHTS = new ModuleSpec(
		"mcwlights",
		"__default_mcw_lights_",
		"Macaw's Lights: ",
		new String[]{
			"candle_holders",
			"ceiling_fan_lights",
			"ceiling_lights",
			"chains",
			"chandeliers",
			"garden_lights",
			"lamps",
			"lanterns",
			"lava_lamps",
			"paper_lamps",
			"slab_lights",
			"soul_street_lamps",
			"soul_tiki_torches",
			"street_lamps",
			"tiki_torches",
			"torches",
			"wall_lamps",
			"wall_lanterns",
		}
	);

	// Paths
	private static final ModuleSpec PATHS = new ModuleSpec(
		"mcwpaths",
		"__default_mcw_paths_",
		"Macaw's Paths: ",
		new String[]{
			"path_stairs",
			"slab_paths",
			"soil_paths",
			"stone_engraved_blocks",
			"stone_paths",
			"stone_pavings",
			"wooden_paths",
		}
	);

	// Stairs
	private static final ModuleSpec STAIRS = new ModuleSpec(
		"mcwstairs",
		"__default_mcw_stairs_",
		"Macaw's Stairs: ",
		new String[]{
			"balconies",
			"bulk_stairs",
			"compact_stairs",
			"loft_stairs",
			"platforms",
			"railings",
			"skyline_stairs",
			"terrace_stairs",
		}
	);


	private static final ModuleSpec[] MODULES = {
		WINDOWS,
		BRIDGES,
		DOORS,
		FENCES,
		FURNITURES,
		LIGHTS,
		PATHS,
		STAIRS,
	};

	@Override
	public int priority() {
		return 700;
	}

	private static GroupFilter mcwBlockTag(String namespace, String tag) {
		return Filters.blockTag(namespace + ":" + tag);
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!Services.CONFIG.shouldLoadMacawsSeries()) return List.of();

		List<GroupDefinition> groups = new ArrayList<>(150);
		for (ModuleSpec module : MODULES) {
			addModuleGroups(groups, module);
		}
		return List.copyOf(groups);
	}

	private static void addModuleGroups(List<GroupDefinition> groups, ModuleSpec module) {
		for (String tag : module.tags()) {
			groups.add(group(
				module.idPrefix() + tag,
				module.displayPrefix() + displayNameForTag(tag),
				mcwBlockTag(module.namespace(), tag)
			));
		}
	}

	private static String displayNameForTag(String tag) {
		return switch (tag) {
			case "wadrobe" -> "Wardrobe";
			case "windows_four" -> "Four Windows";
			case "windows_two" -> "Two Windows";
			default -> titleCase(tag);
		};
	}

	private static String titleCase(String snakeCase) {
		String[] words = snakeCase.split("_");
		StringBuilder builder = new StringBuilder();
		for (String word : words) {
			if (builder.length() > 0) {
				builder.append(' ');
			}
			builder.append(capitalize(word));
		}
		return builder.toString();
	}

	private static String capitalize(String word) {
		if (word.isEmpty()) {
			return word;
		}
		return Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}
}
