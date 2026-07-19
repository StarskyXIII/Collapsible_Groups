package com.starskyxiii.collapsible_groups.group.filter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Version-neutral capability registry for filter-node policy.
 *
 * <p>The 1.21.1 table marks every current node as fully available, preserving existing behavior.
 * A version branch may replace individual entries without changing persistence codecs, matchers,
 * reference extraction, or editor forms. Unknown or unavailable nodes are retained as opaque
 * {@link GroupFilter.Unsupported} values and evaluate to unavailable, so configurations that older
 * builds previously rejected can load inertly and round-trip without format loss.
 */
public final class FilterNodeCapabilities {
	public enum ValidatorBehavior {
		VALIDATE,
		PRESERVE_OPAQUE
	}

	public record Capability(
		boolean available,
		ValidatorBehavior validatorBehavior,
		boolean exposedInEditorConditionMenu,
		boolean kubeJsLoweringSupported
	) {
		public Capability {
			Objects.requireNonNull(validatorBehavior, "validatorBehavior");
		}
	}

	private static final Capability FULL = new Capability(true, ValidatorBehavior.VALIDATE, true, true);
	private static final Capability OPAQUE = new Capability(false, ValidatorBehavior.PRESERVE_OPAQUE, false, false);
	private static final Map<FilterNodeKind, Capability> TABLE = createTable();

	private FilterNodeCapabilities() {}

	public static Capability capability(FilterNodeKind kind) {
		return TABLE.getOrDefault(Objects.requireNonNull(kind, "kind"), OPAQUE);
	}

	public static Map<FilterNodeKind, Capability> all() {
		return TABLE;
	}

	public static boolean isAvailable(FilterNodeKind kind) {
		return capability(kind).available();
	}

	public static boolean isEditorConditionExposed(FilterNodeKind kind) {
		return capability(kind).exposedInEditorConditionMenu();
	}

	public static boolean supportsKubeJsLowering(FilterNodeKind kind) {
		return capability(kind).kubeJsLoweringSupported();
	}

	public static FilterNodeKind kindOf(GroupFilter filter) {
		return switch (Objects.requireNonNull(filter, "filter")) {
			case GroupFilter.Any ignored -> FilterNodeKind.ANY;
			case GroupFilter.All ignored -> FilterNodeKind.ALL;
			case GroupFilter.Not ignored -> FilterNodeKind.NOT;
			case GroupFilter.Id ignored -> FilterNodeKind.ID;
			case GroupFilter.Tag ignored -> FilterNodeKind.TAG;
			case GroupFilter.BlockTag ignored -> FilterNodeKind.BLOCK_TAG;
			case GroupFilter.ItemPathStartsWith ignored -> FilterNodeKind.ITEM_PATH_STARTS_WITH;
			case GroupFilter.ItemPathContains ignored -> FilterNodeKind.ITEM_PATH_CONTAINS;
			case GroupFilter.ItemPathEndsWith ignored -> FilterNodeKind.ITEM_PATH_ENDS_WITH;
			case GroupFilter.Namespace ignored -> FilterNodeKind.NAMESPACE;
			case GroupFilter.ExactStack ignored -> FilterNodeKind.EXACT_STACK;
			case GroupFilter.HasComponent ignored -> FilterNodeKind.HAS_COMPONENT;
			case GroupFilter.ComponentPath ignored -> FilterNodeKind.COMPONENT_PATH;
			case GroupFilter.Unsupported ignored -> FilterNodeKind.UNKNOWN;
		};
	}

	public static boolean containsUnavailable(GroupFilter filter) {
		return switch (Objects.requireNonNull(filter, "filter")) {
			case GroupFilter.Unsupported ignored -> true;
			case GroupFilter.Any any -> any.children().stream().anyMatch(FilterNodeCapabilities::containsUnavailable);
			case GroupFilter.All all -> all.children().stream().anyMatch(FilterNodeCapabilities::containsUnavailable);
			case GroupFilter.Not not -> containsUnavailable(not.child());
			default -> !isAvailable(kindOf(filter));
		};
	}

	public static List<String> unavailableKinds(GroupFilter filter) {
		LinkedHashSet<String> kinds = new LinkedHashSet<>();
		collectUnavailableKinds(Objects.requireNonNull(filter, "filter"), kinds);
		return List.copyOf(kinds);
	}

	private static void collectUnavailableKinds(GroupFilter filter, LinkedHashSet<String> kinds) {
		switch (filter) {
			case GroupFilter.Unsupported unsupported -> kinds.add(unsupported.recognizedKind());
			case GroupFilter.Any any -> any.children().forEach(child -> collectUnavailableKinds(child, kinds));
			case GroupFilter.All all -> all.children().forEach(child -> collectUnavailableKinds(child, kinds));
			case GroupFilter.Not not -> collectUnavailableKinds(not.child(), kinds);
			default -> {
				FilterNodeKind kind = kindOf(filter);
				if (!isAvailable(kind)) kinds.add(kind.name().toLowerCase(java.util.Locale.ROOT));
			}
		}
	}

	private static Map<FilterNodeKind, Capability> createTable() {
		return Map.ofEntries(
			Map.entry(FilterNodeKind.ANY, FULL),
			Map.entry(FilterNodeKind.ALL, FULL),
			Map.entry(FilterNodeKind.NOT, FULL),
			Map.entry(FilterNodeKind.ID, FULL),
			Map.entry(FilterNodeKind.TAG, FULL),
			Map.entry(FilterNodeKind.BLOCK_TAG, FULL),
			Map.entry(FilterNodeKind.ITEM_PATH_STARTS_WITH, FULL),
			Map.entry(FilterNodeKind.ITEM_PATH_CONTAINS, FULL),
			Map.entry(FilterNodeKind.ITEM_PATH_ENDS_WITH, FULL),
			Map.entry(FilterNodeKind.NAMESPACE, FULL),
			Map.entry(FilterNodeKind.EXACT_STACK, FULL),
			Map.entry(FilterNodeKind.HAS_COMPONENT, FULL),
			Map.entry(FilterNodeKind.COMPONENT_PATH, FULL),
			Map.entry(FilterNodeKind.UNKNOWN, OPAQUE)
		);
	}
}
