package com.starskyxiii.collapsible_groups.compat.kubejs;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Stable KubeJS recipe-viewer type IDs for known optional integrations.
 *
 * <p>This catalog is deliberately separate from the runtime ingredient identity registry. It may
 * be queried before a viewer universe exists, but must never reserve canonical viewer identities.
 */
public final class KnownRecipeViewerTypeIds {
	private static final List<Integration> INTEGRATIONS = List.of(
		new Integration("mekanism", List.of("mekanism:chemical", "chemical")),
		new Integration("productivebees", List.of("productivebees:bee", "bee"))
	);

	private KnownRecipeViewerTypeIds() {}

	/**
	 * Returns a deterministic union suitable for KubeJS's one-shot custom-type registry.
	 * Known IDs come first so an early KubeJS initialization still exposes supported integrations;
	 * runtime-discovered IDs are appended when initialization happens after viewer bootstrap.
	 */
	public static List<String> collect(
		Predicate<String> modLoaded,
		Collection<String> runtimeDiscoveredIds
	) {
		Objects.requireNonNull(modLoaded, "modLoaded");
		Objects.requireNonNull(runtimeDiscoveredIds, "runtimeDiscoveredIds");
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (Integration integration : INTEGRATIONS) {
			if (modLoaded.test(integration.modId())) result.addAll(integration.ids());
		}
		result.addAll(runtimeDiscoveredIds);
		return List.copyOf(result);
	}

	private record Integration(String modId, List<String> ids) {}
}
