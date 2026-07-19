package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;

import java.util.Objects;

/** KubeJS-free group DTO emitted by adapter collectors. */
public record KubeJsLoweredGroup(String id, String name, GroupFilter filter) {
	public KubeJsLoweredGroup {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(filter, "filter");
	}
}
