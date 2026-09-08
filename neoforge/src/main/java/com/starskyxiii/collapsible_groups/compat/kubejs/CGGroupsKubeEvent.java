package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweredGroup;
import dev.latvian.mods.kubejs.event.KubeEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.function.Consumer;

public final class CGGroupsKubeEvent implements KubeEvent {
	private final Map<String, List<KubeJsLoweredGroup>> sources = new LinkedHashMap<>();

	public void source(String sourceId, Consumer<CGGroupSourceBuilder> callback) {
		if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("source ID must not be blank");
		String publicationSource = "client:cg:" + sourceId;
		if (sources.containsKey(publicationSource)) throw new IllegalArgumentException("duplicate CG group source: " + sourceId);
		CGGroupSourceBuilder builder = new CGGroupSourceBuilder(sourceId, publicationSource);
		callback.accept(builder);
		sources.put(publicationSource, builder.groups());
	}

	public Map<String, List<KubeJsLoweredGroup>> sources() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(sources));
	}
}
