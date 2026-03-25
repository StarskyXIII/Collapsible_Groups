package com.starskyxiii.collapsible_groups.defaults;

import com.starskyxiii.collapsible_groups.Constants;

import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Centralised discovery for built-in default group providers.
 */
public final class DefaultGroupProviders {

	private static final Comparator<DefaultGroupProvider> ORDER = Comparator
		.comparingInt(DefaultGroupProvider::priority)
		.thenComparing(provider -> provider.getClass().getName());

	private DefaultGroupProviders() {}

	public static List<DefaultGroupProvider> loadAll() {
		return loadAll("unknown", 0);
	}

	public static List<DefaultGroupProvider> loadAll(String loaderName, int minimumExpectedProviders) {
		List<DefaultGroupProvider> providers = ServiceLoader.load(DefaultGroupProvider.class)
			.stream()
			.map(ServiceLoader.Provider::get)
			.sorted(ORDER)
			.toList();

		Constants.LOG.info(
			"[CollapsibleGroups] Loaded {} default group providers: {}",
			providers.size(),
			providers.stream()
				.map(provider -> provider.getClass().getName() + "(priority=" + provider.priority() + ")")
				.toList()
		);
		verifyMinimumProviders(loaderName, minimumExpectedProviders, providers);
		return providers;
	}

	private static void verifyMinimumProviders(String loaderName,
	                                           int minimumExpectedProviders,
	                                           List<DefaultGroupProvider> providers) {
		if (minimumExpectedProviders <= 0) {
			return;
		}

		if (providers.size() < minimumExpectedProviders) {
			Constants.LOG.error(
				"[CollapsibleGroups] Default group provider discovery for {} loaded {} providers, below the expected minimum of {}.",
				loaderName,
				providers.size(),
				minimumExpectedProviders
			);
		}
	}
}
