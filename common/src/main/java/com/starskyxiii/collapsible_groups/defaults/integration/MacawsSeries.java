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
 * Built-in groups for Macaw's series categories.
 * Automatically skipped when none of the supported Macaw's mods are installed.
 */
public final class MacawsSeries implements DefaultGroupProvider {

	private record ModuleSpec(String namespace, String idPrefix, String displayPrefix, String[] tags) {}
	private record PathGroupSpec(String idSuffix, String displayName, GroupFilter filter) {}

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

	private static final PathGroupSpec[] WINDOWS_EXTRAS = {
		new PathGroupSpec(
			"pane_windows",
			"Macaw's Windows: Pane Windows",
			windowPathEndsWith("_pane_window")
		)
	};

	private static final GroupFilter ROOF_ATTIC = roofPathEndsWith("_attic_roof");
	private static final GroupFilter ROOF_TOP = roofPathEndsWith("_top_roof");
	private static final GroupFilter ROOF_UPPER_LOWER = roofPathEndsWith("_upper_lower_roof");
	private static final GroupFilter ROOF_UPPER_STEEP = roofPathEndsWith("_upper_steep_roof");
	private static final GroupFilter ROOF_LOWER = roofPathEndsWithExcluding("_lower_roof", ROOF_UPPER_LOWER);
	private static final GroupFilter ROOF_STEEP = roofPathEndsWithExcluding("_steep_roof", ROOF_UPPER_STEEP);
	private static final GroupFilter ROOF_BASE = Filters.all(
		roofPathEndsWith("_roof"),
		Filters.not(Filters.any(
			ROOF_ATTIC,
			ROOF_LOWER,
			ROOF_STEEP,
			ROOF_TOP,
			ROOF_UPPER_LOWER,
			ROOF_UPPER_STEEP
		))
	);

	private static final PathGroupSpec[] ROOFS = {
		new PathGroupSpec(
			"attic_roofs",
			"Macaw's Roofs: Attic Roofs",
			ROOF_ATTIC
		),
		new PathGroupSpec(
			"lower_roofs",
			"Macaw's Roofs: Lower Roofs",
			ROOF_LOWER
		),
		new PathGroupSpec(
			"roofs",
			"Macaw's Roofs: Roofs",
			ROOF_BASE
		),
		new PathGroupSpec(
			"steep_roofs",
			"Macaw's Roofs: Steep Roofs",
			ROOF_STEEP
		),
		new PathGroupSpec(
			"top_roofs",
			"Macaw's Roofs: Top Roofs",
			ROOF_TOP
		),
		new PathGroupSpec(
			"upper_lower_roofs",
			"Macaw's Roofs: Upper Lower Roofs",
			ROOF_UPPER_LOWER
		),
		new PathGroupSpec(
			"upper_steep_roofs",
			"Macaw's Roofs: Upper Steep Roofs",
			ROOF_UPPER_STEEP
		),
		new PathGroupSpec(
			"gutter_bases",
			"Macaw's Roofs: Gutter Bases",
			roofPathStartsWith("gutter_base")
		),
		new PathGroupSpec(
			"gutter_middles",
			"Macaw's Roofs: Gutter Middles",
			roofPathStartsWith("gutter_middle")
		),
		new PathGroupSpec(
			"striped_awnings",
			"Macaw's Roofs: Striped Awnings",
			roofPathEndsWith("_striped_awning")
		),
		new PathGroupSpec(
			"roof_blocks",
			"Macaw's Roofs: Roof Blocks",
			roofPathEndsWith("_roof_block")
		),
		new PathGroupSpec(
			"roof_slabs",
			"Macaw's Roofs: Roof Slabs",
			roofPathEndsWith("_roof_slab")
		)
	};

	private static final PathGroupSpec[] TRAPDOORS = {
		new PathGroupSpec(
			"bamboo_trapdoors",
			"Macaw's Trapdoors: Bamboo Trapdoors",
			Filters.any(
				trapdoorPathEndsWith("_bamboo_trapdoor"),
				Filters.itemId("mcwtrpdoors:bamboo_trapdoor")
			)
		),
		new PathGroupSpec(
			"bark_trapdoors",
			"Macaw's Trapdoors: Bark Trapdoors",
			trapdoorPathEndsWith("_bark_trapdoor")
		),
		new PathGroupSpec(
			"barn_trapdoors",
			"Macaw's Trapdoors: Barn Trapdoors",
			trapdoorPathEndsWith("_barn_trapdoor")
		),
		new PathGroupSpec(
			"barred_trapdoors",
			"Macaw's Trapdoors: Barred Trapdoors",
			trapdoorPathEndsWith("_barred_trapdoor")
		),
		new PathGroupSpec(
			"barrel_trapdoors",
			"Macaw's Trapdoors: Barrel Trapdoors",
			trapdoorPathEndsWith("_barrel_trapdoor")
		),
		new PathGroupSpec(
			"beach_trapdoors",
			"Macaw's Trapdoors: Beach Trapdoors",
			trapdoorPathEndsWith("_beach_trapdoor")
		),
		new PathGroupSpec(
			"blossom_trapdoors",
			"Macaw's Trapdoors: Blossom Trapdoors",
			trapdoorPathEndsWith("_blossom_trapdoor")
		),
		new PathGroupSpec(
			"classic_trapdoors",
			"Macaw's Trapdoors: Classic Trapdoors",
			trapdoorPathEndsWith("_classic_trapdoor")
		),
		new PathGroupSpec(
			"cottage_trapdoors",
			"Macaw's Trapdoors: Cottage Trapdoors",
			trapdoorPathEndsWith("_cottage_trapdoor")
		),
		new PathGroupSpec(
			"four_panel_trapdoors",
			"Macaw's Trapdoors: Four Panel Trapdoors",
			trapdoorPathEndsWith("_four_panel_trapdoor")
		),
		new PathGroupSpec(
			"glass_trapdoors",
			"Macaw's Trapdoors: Glass Trapdoors",
			trapdoorPathEndsWith("_glass_trapdoor")
		),
		new PathGroupSpec(
			"metal_trapdoors",
			"Macaw's Trapdoors: Metal Trapdoors",
			trapdoorPathStartsWith("metal_")
		),
		new PathGroupSpec(
			"mystic_trapdoors",
			"Macaw's Trapdoors: Mystic Trapdoors",
			trapdoorPathEndsWith("_mystic_trapdoor")
		),
		new PathGroupSpec(
			"paper_trapdoors",
			"Macaw's Trapdoors: Paper Trapdoors",
			trapdoorPathEndsWith("_paper_trapdoor")
		),
		new PathGroupSpec(
			"ranch_trapdoors",
			"Macaw's Trapdoors: Ranch Trapdoors",
			trapdoorPathEndsWith("_ranch_trapdoor")
		),
		new PathGroupSpec(
			"swamp_trapdoors",
			"Macaw's Trapdoors: Swamp Trapdoors",
			trapdoorPathEndsWith("_swamp_trapdoor")
		),
		new PathGroupSpec(
			"tropical_trapdoors",
			"Macaw's Trapdoors: Tropical Trapdoors",
			trapdoorPathEndsWith("_tropical_trapdoor")
		),
		new PathGroupSpec(
			"whispering_trapdoors",
			"Macaw's Trapdoors: Whispering Trapdoors",
			trapdoorPathEndsWith("_whispering_trapdoor")
		),
	};


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

	private static GroupFilter windowPathEndsWith(String suffix) {
		return Filters.all(
			Filters.itemNamespace("mcwwindows"),
			Filters.itemPathEndsWith(suffix)
		);
	}

	private static GroupFilter roofPathStartsWith(String prefix) {
		return Filters.all(
			Filters.itemNamespace("mcwroofs"),
			Filters.itemPathStartsWith(prefix)
		);
	}

	private static GroupFilter roofPathEndsWith(String suffix) {
		return Filters.all(
			Filters.itemNamespace("mcwroofs"),
			Filters.itemPathEndsWith(suffix)
		);
	}

	private static GroupFilter roofPathEndsWithExcluding(String suffix, GroupFilter... excluded) {
		return Filters.all(
			roofPathEndsWith(suffix),
			Filters.not(Filters.any(excluded))
		);
	}

	private static GroupFilter trapdoorPathStartsWith(String prefix) {
		return Filters.all(
			Filters.itemNamespace("mcwtrpdoors"),
			Filters.itemPathStartsWith(prefix)
		);
	}

	private static GroupFilter trapdoorPathEndsWith(String suffix) {
		return Filters.all(
			Filters.itemNamespace("mcwtrpdoors"),
			Filters.itemPathEndsWith(suffix)
		);
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!Services.CONFIG.shouldLoadMacawsSeries()) return List.of();

		List<GroupDefinition> groups = new ArrayList<>(150);
		for (ModuleSpec module : MODULES) {
			addModuleGroups(groups, module);
		}
		addWindowGroups(groups);
		addRoofGroups(groups);
		addTrapdoorGroups(groups);
		return List.copyOf(groups);
	}

	private static void addModuleGroups(List<GroupDefinition> groups, ModuleSpec module) {
		for (String tag : module.tags()) {
			groups.add(group(
				module.idPrefix() + tag,
				module.displayPrefix() + displayNameForTag(tag),
				moduleFilter(module, tag)
			));
		}
	}

	private static GroupFilter moduleFilter(ModuleSpec module, String tag) {
		if ("mcwwindows".equals(module.namespace()) && "shutters".equals(tag)) {
			return Filters.any(
				mcwBlockTag(module.namespace(), tag),
				Filters.itemId("mcwwindows:iron_shutter")
			);
		}
		if ("mcwfences".equals(module.namespace()) && "modern_walls".equals(tag)) {
			return Filters.any(
				mcwBlockTag(module.namespace(), tag),
				Filters.itemId("mcwfences:modern_prismarine_wall")
			);
		}
		return mcwBlockTag(module.namespace(), tag);
	}

	private static void addWindowGroups(List<GroupDefinition> groups) {
		for (PathGroupSpec spec : WINDOWS_EXTRAS) {
			groups.add(group(
				"__default_mcw_windows_" + spec.idSuffix(),
				spec.displayName(),
				spec.filter()
			));
		}
	}

	private static void addRoofGroups(List<GroupDefinition> groups) {
		for (PathGroupSpec spec : ROOFS) {
			groups.add(group(
				"__default_mcw_roofs_" + spec.idSuffix(),
				spec.displayName(),
				spec.filter()
			));
		}
	}

	private static void addTrapdoorGroups(List<GroupDefinition> groups) {
		for (PathGroupSpec spec : TRAPDOORS) {
			groups.add(group(
				"__default_mcw_trapdoors_" + spec.idSuffix(),
				spec.displayName(),
				spec.filter()
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
