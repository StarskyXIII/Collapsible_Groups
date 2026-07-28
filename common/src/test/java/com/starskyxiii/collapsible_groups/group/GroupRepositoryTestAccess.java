package com.starskyxiii.collapsible_groups.group;

import java.util.List;

/** Package bridge that keeps the repository's deterministic reset seam out of production API. */
public final class GroupRepositoryTestAccess {
	private GroupRepositoryTestAccess() {}

	public static void replace(List<GroupDefinition> groups) {
		GroupRepository.replaceForTesting(groups);
	}
}
