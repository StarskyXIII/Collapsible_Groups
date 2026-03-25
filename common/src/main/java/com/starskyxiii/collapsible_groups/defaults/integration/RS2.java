package com.starskyxiii.collapsible_groups.defaults.integration;

import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import com.starskyxiii.collapsible_groups.platform.Services;

import java.util.List;

import static com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider.group;

/**
 * Built-in groups for Refined Storage 2.
 * Automatically skipped if RS2 is not installed.
 */
public final class RS2 implements DefaultGroupProvider {
	@Override
	public int priority() {
		return 650;
	}

	@Override
	public List<GroupDefinition> getGroups() {
		if (!Services.CONFIG.shouldLoadRS2()) return List.of();
		return List.of(
			group("__default_refinedstorage_colored_controllers", "RS2: Colored Controllers", Filters.all(Filters.itemTag("refinedstorage:controllers"), Filters.not(Filters.itemId("refinedstorage:controller")))),
			group("__default_refinedstorage_colored_creative_controllers", "RS2: Colored Creative Controllers", Filters.all(Filters.itemTag("refinedstorage:creative_controllers"), Filters.not(Filters.itemId("refinedstorage:creative_controller")))),
			group("__default_refinedstorage_colored_cables", "RS2: Colored Cables", Filters.all(Filters.itemTag("refinedstorage:cables"), Filters.not(Filters.itemId("refinedstorage:cable")))),
			group("__default_refinedstorage_colored_importers", "RS2: Colored Importers", Filters.all(Filters.itemTag("refinedstorage:importers"), Filters.not(Filters.itemId("refinedstorage:importer")))),
			group("__default_refinedstorage_colored_exporters", "RS2: Colored Exporters", Filters.all(Filters.itemTag("refinedstorage:exporters"), Filters.not(Filters.itemId("refinedstorage:exporter")))),
			group("__default_refinedstorage_colored_external_storages", "RS2: Colored External Storages", Filters.all(Filters.itemTag("refinedstorage:external_storages"), Filters.not(Filters.itemId("refinedstorage:external_storage")))),
			group("__default_refinedstorage_colored_constructors", "RS2: Colored Constructors", Filters.all(Filters.itemTag("refinedstorage:constructors"), Filters.not(Filters.itemId("refinedstorage:constructor")))),
			group("__default_refinedstorage_colored_destructors", "RS2: Colored Destructors", Filters.all(Filters.itemTag("refinedstorage:destructors"), Filters.not(Filters.itemId("refinedstorage:destructor")))),
			group("__default_refinedstorage_colored_wireless_transmitters", "RS2: Colored Wireless Transmitters", Filters.all(Filters.itemTag("refinedstorage:wireless_transmitters"), Filters.not(Filters.itemId("refinedstorage:wireless_transmitter")))),
			group("__default_refinedstorage_colored_grids", "RS2: Colored Grids", Filters.all(Filters.itemTag("refinedstorage:grids"), Filters.not(Filters.itemId("refinedstorage:grid")))),
			group("__default_refinedstorage_colored_crafting_grids", "RS2: Colored Crafting Grids", Filters.all(Filters.itemTag("refinedstorage:crafting_grids"), Filters.not(Filters.itemId("refinedstorage:crafting_grid")))),
			group("__default_refinedstorage_colored_pattern_grids", "RS2: Colored Pattern Grids", Filters.all(Filters.itemTag("refinedstorage:pattern_grids"), Filters.not(Filters.itemId("refinedstorage:pattern_grid")))),
			group("__default_refinedstorage_colored_detectors", "RS2: Colored Detectors", Filters.all(Filters.itemTag("refinedstorage:detectors"), Filters.not(Filters.itemId("refinedstorage:detector")))),
			group("__default_refinedstorage_colored_network_transmitters", "RS2: Colored Network Transmitters", Filters.all(Filters.itemTag("refinedstorage:network_transmitters"), Filters.not(Filters.itemId("refinedstorage:network_transmitter")))),
			group("__default_refinedstorage_colored_network_receivers", "RS2: Colored Network Receivers", Filters.all(Filters.itemTag("refinedstorage:network_receivers"), Filters.not(Filters.itemId("refinedstorage:network_receiver")))),
			group("__default_refinedstorage_colored_security_managers", "RS2: Colored Security Managers", Filters.all(Filters.itemTag("refinedstorage:security_managers"), Filters.not(Filters.itemId("refinedstorage:security_manager")))),
			group("__default_refinedstorage_colored_relays", "RS2: Colored Relays", Filters.all(Filters.itemTag("refinedstorage:relays"), Filters.not(Filters.itemId("refinedstorage:relay")))),
			group("__default_refinedstorage_colored_disk_interfaces", "RS2: Colored Disk Interfaces", Filters.all(Filters.itemTag("refinedstorage:disk_interfaces"), Filters.not(Filters.itemId("refinedstorage:disk_interface")))),
			group("__default_refinedstorage_colored_autocrafters", "RS2: Colored Autocrafters", Filters.all(Filters.itemTag("refinedstorage:autocrafters"), Filters.not(Filters.itemId("refinedstorage:autocrafter")))),
			group("__default_refinedstorage_colored_autocrafter_managers", "RS2: Colored Autocrafter Managers", Filters.all(Filters.itemTag("refinedstorage:autocrafter_managers"), Filters.not(Filters.itemId("refinedstorage:autocrafter_manager")))),
			group("__default_refinedstorage_colored_autocrafting_monitors", "RS2: Colored Autocrafting Monitors", Filters.all(Filters.itemTag("refinedstorage:autocrafting_monitors"), Filters.not(Filters.itemId("refinedstorage:autocrafting_monitor"))))
		);
	}
}
