package com.starskyxiii.collapsible_groups.defaults.integration;

import com.starskyxiii.collapsible_groups.config.NeoForgeConfig;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.*;

import java.util.List;

/**
 * Built-in groups for AE2.
 * Automatically skipped if AE2 is not installed.
 */
public final class AE2 implements DefaultGroupProvider {

	@Override
	public int priority() {
		return 300;
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!NeoForgeConfig.shouldLoadAE2()) return List.of();
		return List.of(
			group("__default_ae2_paint_balls",   "AE2: Paint Balls", Filters.itemTag("ae2:paint_balls")),
			group("__default_ae2_lumen_paint_balls",   "AE2: Lumen Paint Balls", Filters.itemTag("ae2:lumen_paint_balls")),
			group("__default_ae2_colored_smart_cables",   "AE2: Colored Smart Cables", Filters.all(Filters.itemTag("ae2:smart_cable"), Filters.not(Filters.itemId("ae2:fluix_smart_cable")))),
			group("__default_ae2_colored_covered_cables",   "AE2: Colored Covered Cables", Filters.all(Filters.itemTag("ae2:covered_cable"), Filters.not(Filters.itemId("ae2:fluix_covered_cable")))),
			group("__default_ae2_colored_glass_cables",   "AE2: Colored Glass Cables", Filters.all(Filters.itemTag("ae2:glass_cable"), Filters.not(Filters.itemId("ae2:fluix_glass_cable")))),
			group("__default_ae2_colored_covered_dense_cables",   "AE2: Colored Dense Covered Cables", Filters.all(Filters.itemTag("ae2:covered_dense_cable"), Filters.not(Filters.itemId("ae2:fluix_covered_dense_cable")))),
			group("__default_ae2_colored_smart_dense_cables",   "AE2: Colored Dense Smart Cables", Filters.all(Filters.itemTag("ae2:smart_dense_cable"), Filters.not(Filters.itemId("ae2:fluix_smart_dense_cable"))))
		);
	}
}
