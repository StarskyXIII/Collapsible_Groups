package com.starskyxiii.collapsible_groups.compat.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;

public final class CollapsibleGroupsKubeJSPlugin extends KubeJSPlugin {
	@Override
	public void registerEvents() {
		CGEvents.GROUP.register();
	}
}
