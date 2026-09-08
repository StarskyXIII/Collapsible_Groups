package com.starskyxiii.collapsible_groups.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventJS;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class CGGroupsKubeEvent extends EventJS {
	private final Map<String, List<KubeJsLoweredGroup>> sources = new LinkedHashMap<>();

	public void source(String sourceId, Consumer<CGGroupSourceBuilder> callback) {
		if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("source ID must not be blank");
		if (callback == null) throw new NullPointerException("callback");
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
