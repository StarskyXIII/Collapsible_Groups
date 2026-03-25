package com.starskyxiii.collapsible_groups.defaults.integration;

import com.starskyxiii.collapsible_groups.config.NeoForgeConfig;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.*;

import java.util.List;

/**
 * Built-in groups for EnderIO.
 * Automatically skipped if EnderIO is not installed.
 */
public final class EnderIO implements DefaultGroupProvider {

	@Override
	public int priority() {
		return 400;
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!NeoForgeConfig.shouldLoadEnderIO()) return List.of();
		return List.of(
			group("__default_enderio_fused_quartz", "Fused Quartzs",
				tag("c:glass_blocks/fused_quartz")
			),
			group("__default_enderio_clear_glass", "Clear Glasses",
				tag("c:glass_blocks/clear")
			),
			group("__default_enderio_filled_soul_vials", "Filled Soul vials",
				Filters.all(
					Filters.itemId("enderio:soul_vial"),
					Filters.not(Filters.exactStack("{\"id\":\"enderio:soul_vial\"}"))
				)
			)
		);
	}
}
