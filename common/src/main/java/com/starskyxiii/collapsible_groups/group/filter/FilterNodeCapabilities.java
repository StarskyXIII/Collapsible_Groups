package com.starskyxiii.collapsible_groups.group.filter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Version-neutral capability registry for filter-node policy.
 *
 * <p>A version branch may replace individual entries without changing persistence codecs, matchers,
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

	private static final Map<FilterNodeKind, Capability> TABLE = createTable();

	private FilterNodeCapabilities() {}

	public static Capability capability(FilterNodeKind kind) {
		return TABLE.get(Objects.requireNonNull(kind, "kind"));
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
		Objects.requireNonNull(filter, "filter");
		if (filter instanceof GroupFilter.Any) return FilterNodeKind.ANY;
		if (filter instanceof GroupFilter.All) return FilterNodeKind.ALL;
		if (filter instanceof GroupFilter.Not) return FilterNodeKind.NOT;
		if (filter instanceof GroupFilter.Id) return FilterNodeKind.ID;
		if (filter instanceof GroupFilter.Tag) return FilterNodeKind.TAG;
		if (filter instanceof GroupFilter.BlockTag) return FilterNodeKind.BLOCK_TAG;
		if (filter instanceof GroupFilter.ItemPathStartsWith) return FilterNodeKind.ITEM_PATH_STARTS_WITH;
		if (filter instanceof GroupFilter.ItemPathContains) return FilterNodeKind.ITEM_PATH_CONTAINS;
		if (filter instanceof GroupFilter.ItemPathEndsWith) return FilterNodeKind.ITEM_PATH_ENDS_WITH;
		if (filter instanceof GroupFilter.Namespace) return FilterNodeKind.NAMESPACE;
		if (filter instanceof GroupFilter.ExactStack) return FilterNodeKind.EXACT_STACK;
		if (filter instanceof GroupFilter.Nbt) return FilterNodeKind.NBT;
		if (filter instanceof GroupFilter.NbtPath) return FilterNodeKind.NBT_PATH;
		if (filter instanceof GroupFilter.HasComponent) return FilterNodeKind.HAS_COMPONENT;
		if (filter instanceof GroupFilter.ComponentPath) return FilterNodeKind.COMPONENT_PATH;
		return FilterNodeKind.UNKNOWN;
	}

	public static boolean containsUnavailable(GroupFilter filter) {
		Objects.requireNonNull(filter, "filter");
		if (filter instanceof GroupFilter.Unsupported) return true;
		if (filter instanceof GroupFilter.Any) return ((GroupFilter.Any) filter).children().stream().anyMatch(FilterNodeCapabilities::containsUnavailable);
		if (filter instanceof GroupFilter.All) return ((GroupFilter.All) filter).children().stream().anyMatch(FilterNodeCapabilities::containsUnavailable);
		if (filter instanceof GroupFilter.Not) return containsUnavailable(((GroupFilter.Not) filter).child());
		return !isAvailable(kindOf(filter));
	}

	public static List<String> unavailableKinds(GroupFilter filter) {
		LinkedHashSet<String> kinds = new LinkedHashSet<>();
		collectUnavailableKinds(Objects.requireNonNull(filter, "filter"), kinds);
		return List.copyOf(kinds);
	}

	private static void collectUnavailableKinds(GroupFilter filter, LinkedHashSet<String> kinds) {
		if (filter instanceof GroupFilter.Unsupported) {
			kinds.add(((GroupFilter.Unsupported) filter).recognizedKind());
		} else if (filter instanceof GroupFilter.Any) {
			((GroupFilter.Any) filter).children().forEach(child -> collectUnavailableKinds(child, kinds));
		} else if (filter instanceof GroupFilter.All) {
			((GroupFilter.All) filter).children().forEach(child -> collectUnavailableKinds(child, kinds));
		} else if (filter instanceof GroupFilter.Not) {
			collectUnavailableKinds(((GroupFilter.Not) filter).child(), kinds);
		} else {
			FilterNodeKind kind = kindOf(filter);
			if (!isAvailable(kind)) kinds.add(kind.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private static Map<FilterNodeKind, Capability> createTable() {
		java.util.EnumMap<FilterNodeKind, Capability> table = new java.util.EnumMap<>(FilterNodeKind.class);
		RuleDescriptor.all().forEach((kind, descriptor) -> table.put(kind, new Capability(
			descriptor.available(),
			descriptor.available() ? ValidatorBehavior.VALIDATE : ValidatorBehavior.PRESERVE_OPAQUE,
			descriptor.available(),
			descriptor.kubeJsLoweringSupported()
		)));
		return java.util.Collections.unmodifiableMap(table);
	}
}
