package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;

import java.util.List;

/** Consumer boundary used after version-specific KubeJS collection completes. */
@FunctionalInterface
public interface KubeJsGroupConsumer {
	void replace(List<GroupDefinition> groups);
}
