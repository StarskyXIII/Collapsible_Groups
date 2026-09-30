package com.starskyxiii.collapsible_groups.compat.kubejs;

import java.util.List;

/** KubeJS-free view of groups collected by a version-specific event adapter. */
public interface KubeJsGroupCollector {
	List<KubeJsLoweredGroup> collectedGroups();
}
