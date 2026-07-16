package com.starskyxiii.collapsible_groups.i18n;

import com.starskyxiii.collapsible_groups.group.GroupDisplayName;

/**
 * Central bootstrap for built-in group translation deployment and overlay
 * reload. Call this from loader startup and client resource reload hooks.
 */
public final class GroupLangBootstrap {

	private GroupLangBootstrap() {}

	/**
	 * Deploys the bundled group lang for the current locale, then reloads the
	 * overlay cache (managed by {@link GroupTranslationHelper}, consumed by {@link com.starskyxiii.collapsible_groups.group.GroupDisplayName} when resolving display text).
	 */
	public static void refresh() {
		GroupLangDeployer.deployCurrentLocale();
		GroupTranslationHelper.reloadOverlay();
	}
}
