package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.ScriptedGroupStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KubeJsGroupPublication {
	private KubeJsGroupPublication() {}

	public static Session begin(String ownerId) {
		return new Session(ownerId, ScriptedGroupStore.beginPublication(ownerId));
	}

	public static final class Session {
		private final String ownerId;
		private final long generation;
		private final Map<String, KubeJsMaterializationCapture> captures = new LinkedHashMap<>();
		private final Map<String, List<GroupDefinition>> replacements = new LinkedHashMap<>();
		private boolean published;
		private boolean failed;

		private Session(String ownerId, long generation) {
			if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
			this.ownerId = ownerId;
			this.generation = generation;
		}

		public long generation() { return generation; }

		public KubeJsMaterializationCapture capture(String sourceId) {
			ensureOpen();
			validateSource(sourceId);
			return captures.computeIfAbsent(sourceId, source ->
				new KubeJsMaterializationCapture(source, generation,
					ScriptedGroupStore.nextMaterializationCapture()));
		}

		public Session replace(String sourceId, List<KubeJsLoweredGroup> groups) {
			ensureOpen();
			try {
				validateSource(sourceId);
				if (groups == null) throw new NullPointerException("groups");
				List<GroupDefinition> definitions = groups.stream()
					.map(group -> toDefinition(sourceId, group))
					.toList();
				replacements.put(sourceId, definitions);
				return this;
			} catch (RuntimeException failure) {
				failed = true;
				throw failure;
			}
		}

		public boolean publish() {
			ensureOpen();
			published = true;
			return ScriptedGroupStore.publishSources(ownerId, generation, replacements);
		}

		private GroupDefinition toDefinition(String sourceId, KubeJsLoweredGroup group) {
			if (group == null) throw new IllegalArgumentException("group source contains null");
			KubeJsLoweringResult result = group.lowering();
			if (result.kind() == KubeJsLoweringResult.Kind.UNSUPPORTED) {
				throw new IllegalArgumentException("unsupported lowering for group " + group.id() + ": " + result.reason());
			}
			if (result.kind() == KubeJsLoweringResult.Kind.MATERIALIZED) {
				KubeJsMaterializationCapture expected = captures.get(sourceId);
				KubeJsMaterializationCapture actual = result.materialization().orElseThrow();
				if (expected == null || !expected.equals(actual) || !sourceId.equals(result.source())) {
					throw new IllegalArgumentException("materialized lowering capture does not match source session");
				}
			}
			return new GroupDefinition(group.id(), group.name(), true, result.filter().orElseThrow());
		}

		private void ensureOpen() {
			if (published) throw new IllegalStateException("publication session is closed");
			if (failed) throw new IllegalStateException("publication session has a rejected source");
		}

		private static void validateSource(String sourceId) {
			if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("sourceId must not be blank");
		}
	}
}
