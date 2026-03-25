package com.starskyxiii.collapsible_groups.core;

import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents the display name of a collapsible group with built-in localisation support.
 *
 * <p>Every group automatically receives a stable translation key plus a fallback name.
 * The resolution order is:
 * <ol>
 *   <li>Config overlay ({@code config/collapsiblegroups/lang/<locale>.json})</li>
 *   <li>Minecraft {@code Language} system ({@code assets/.../lang/*.json})</li>
 *   <li>Fallback plain-text name</li>
 * </ol>
 *
 * <p>{@code displayName()} on {@link GroupDefinition} is the <b>authoritative</b> data
 * source for persistence, dump, and editor operations.  {@code name()} is a convenience
 * accessor that returns the resolved display text for the current language.
 */
public sealed interface GroupDisplayName {

	/**
	 * A localisable group name with a stable translation key and a plain-text fallback.
	 *
	 * @param key      translation key, e.g. {@code "collapsible_groups.group.spawn_eggs"}
	 * @param fallback plain-text fallback shown when no translation exists
	 */
	record Localized(String key, String fallback) implements GroupDisplayName {
		public Localized {
			Objects.requireNonNull(key, "translation key must not be null");
			Objects.requireNonNull(fallback, "fallback must not be null");
		}
	}

	/**
	 * Returns a {@link Component} suitable for rendering.
	 * Uses the full resolution chain: overlay -> Minecraft lang -> fallback.
	 */
	default Component toComponent() {
		Optional<String> overlayValue = GroupTranslationHelper.lookupOverlay(key());
		if (overlayValue.isPresent()) {
			return Component.literal(overlayValue.get());
		}
		return Component.translatableWithFallback(key(), fallback());
	}

	/**
	 * Resolves the display text for the active Minecraft client language.
	 * Resolution order: overlay -> Minecraft lang -> fallback.
	 *
	 * <p>This is a client-side convenience for UI rendering and should not be
	 * treated as a server-neutral or persistence-safe API.
	 */
	default String resolveClientDisplayText() {
		Optional<String> overlayValue = GroupTranslationHelper.lookupOverlay(key());
		if (overlayValue.isPresent()) {
			return overlayValue.get();
		}
		// Resolve via Component.translatableWithFallback; falls back to the plain-text name when no translation is found
		return Component.translatableWithFallback(key(), fallback()).getString();
	}

	/** Translation key auto-derived from the group ID, in the format {@code collapsible_groups.group.<id>}. */
	String key();

	String fallback();
}
