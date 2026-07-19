package com.starskyxiii.collapsible_groups.compat.kubejs;

import java.util.Objects;

/** KubeJS group-ID formulas shared by version-specific adapters. */
public final class KubeJsGroupIds {
	private KubeJsGroupIds() {}

	public static String item(String groupId) {
		return "__kjs_" + sanitize(groupId);
	}

	public static String fluid(String groupId) {
		return "__kjs_fluid_" + sanitize(groupId);
	}

	public static String generic(String typeId, String groupId) {
		return "__kjs_" + sanitize(typeId) + '_' + sanitize(groupId);
	}

	public static String remoteItem(String groupId) {
		return "__kjs_remote_" + sanitize(groupId);
	}

	public static String remoteFluid(String groupId) {
		return "__kjs_remote_fluid_" + sanitize(groupId);
	}

	private static String sanitize(String value) {
		return Objects.requireNonNull(value, "value").replace(':', '_').replace('/', '_');
	}
}
