package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.filter.FilterNodeCapabilities;
import com.starskyxiii.collapsible_groups.group.filter.FilterNodeKind;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** KubeJS-free composition of already-lowered filter DTOs. */
public final class KubeJsFilterComposition {
	private KubeJsFilterComposition() {}

	public static @Nullable GroupFilter any(List<GroupFilter> nodes) {
		Objects.requireNonNull(nodes, "nodes");
		if (!FilterNodeCapabilities.supportsKubeJsLowering(FilterNodeKind.ANY)
			|| nodes.stream().anyMatch(node -> !supportsTree(node))) {
			return null;
		}
		if (nodes.isEmpty()) return null;
		if (nodes.size() == 1) return nodes.get(0);
		return Filters.any(nodes.toArray(GroupFilter[]::new));
	}

	public static @Nullable GroupFilter all(List<GroupFilter> nodes) {
		Objects.requireNonNull(nodes, "nodes");
		if (!FilterNodeCapabilities.supportsKubeJsLowering(FilterNodeKind.ALL)
			|| nodes.stream().anyMatch(node -> !supportsTree(node))) {
			return null;
		}
		if (nodes.isEmpty()) return null;
		if (nodes.size() == 1) return nodes.get(0);
		return Filters.all(nodes.toArray(GroupFilter[]::new));
	}

	public static boolean supportsTree(GroupFilter filter) {
		if (!FilterNodeCapabilities.supportsKubeJsLowering(FilterNodeCapabilities.kindOf(filter))) return false;
		if (filter instanceof GroupFilter.Any) return ((GroupFilter.Any) filter).children().stream().allMatch(KubeJsFilterComposition::supportsTree);
		if (filter instanceof GroupFilter.All) return ((GroupFilter.All) filter).children().stream().allMatch(KubeJsFilterComposition::supportsTree);
		if (filter instanceof GroupFilter.Not) return supportsTree(((GroupFilter.Not) filter).child());
		return !(filter instanceof GroupFilter.Unsupported);
	}
}
