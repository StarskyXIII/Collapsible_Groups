package com.starskyxiii.collapsible_groups.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public interface CGEvents {
	EventGroup GROUP = EventGroup.of("CGEvents");
	EventHandler GROUPS = GROUP.client("groups", () -> CGGroupsKubeEvent.class);
}
