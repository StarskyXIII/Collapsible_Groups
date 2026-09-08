package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.viewer.ViewerBootstrapContext;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KubeJSGroupBridge {
	private static final String OWNER = "multiloader:kubejs6";

	private KubeJSGroupBridge() {}

	public static void applyGroupsNeutral(ViewerBootstrapContext<?> ignored) {
		applyGroups();
	}

	public static void applyGroups() {
		KubeJsGroupPublication.Session publication = KubeJsGroupPublication.begin(OWNER);
		if (CGEvents.GROUPS.hasListeners()) {
			CGGroupsKubeEvent event = new CGGroupsKubeEvent();
			AtomicBoolean failed = new AtomicBoolean();
			EventResult result = CGEvents.GROUPS.post(ScriptType.CLIENT, event, (posted, container, error) -> {
				failed.set(true);
				return error;
			});
			if (failed.get() || result.error()) return;
			for (var entry : event.sources().entrySet()) {
				publication.replace(entry.getKey(), acceptedGroups(entry.getKey(), entry.getValue()));
			}
		}
		publication.publish();
	}

	static List<KubeJsLoweredGroup> acceptedGroups(String source, List<KubeJsLoweredGroup> groups) {
		List<KubeJsLoweredGroup> accepted = new ArrayList<>(groups.size());
		for (KubeJsLoweredGroup group : groups) {
			if (group.lowering().kind() == KubeJsLoweringResult.Kind.UNSUPPORTED) {
				Constants.LOG.warn("[CollapsibleGroups] Rejecting KubeJS group '{}' from source '{}': {}",
					group.id(), source, group.lowering().reason());
			} else {
				accepted.add(group);
			}
		}
		return List.copyOf(accepted);
	}
}
