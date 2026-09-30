package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;

import java.util.Objects;
import java.util.Optional;

public record KubeJsLoweringResult(
	Kind kind,
	Optional<GroupFilter> filter,
	String reason,
	String source,
	Optional<KubeJsMaterializationCapture> materialization
) {
	public enum Kind { EXACT, MATERIALIZED, UNSUPPORTED }

	public KubeJsLoweringResult {
		Objects.requireNonNull(kind, "kind");
		filter = Objects.requireNonNull(filter, "filter");
		reason = Objects.requireNonNull(reason, "reason");
		if (source == null || source.isBlank()) throw new IllegalArgumentException("source must not be blank");
		materialization = Objects.requireNonNull(materialization, "materialization");
		if (kind == Kind.UNSUPPORTED && filter.isPresent()) {
			throw new IllegalArgumentException("unsupported lowering must not contain a filter");
		}
		if (kind == Kind.UNSUPPORTED && reason.isBlank()) {
			throw new IllegalArgumentException("unsupported lowering requires a reason");
		}
		if (kind != Kind.UNSUPPORTED && filter.isEmpty()) {
			throw new IllegalArgumentException("supported lowering requires a filter");
		}
		if (kind == Kind.MATERIALIZED && materialization.isEmpty()) {
			throw new IllegalArgumentException("materialized lowering requires a capture");
		}
		if (kind != Kind.MATERIALIZED && materialization.isPresent()) {
			throw new IllegalArgumentException("only materialized lowering may contain a capture");
		}
		if (kind == Kind.MATERIALIZED && !source.equals(materialization.orElseThrow().source())) {
			throw new IllegalArgumentException("materialization source must match lowering source");
		}
		if (kind == Kind.MATERIALIZED && !isMaterializedIdSet(filter.orElseThrow())) {
			throw new IllegalArgumentException("materialized lowering must contain only resource ID nodes");
		}
	}

	public static KubeJsLoweringResult exact(GroupFilter filter, String source) {
		return new KubeJsLoweringResult(Kind.EXACT, Optional.of(Objects.requireNonNull(filter, "filter")),
			"", source, Optional.empty());
	}

	public static KubeJsLoweringResult materialized(GroupFilter filter, String source,
		KubeJsMaterializationCapture capture) {
		return new KubeJsLoweringResult(Kind.MATERIALIZED,
			Optional.of(Objects.requireNonNull(filter, "filter")), "", source,
			Optional.of(Objects.requireNonNull(capture, "capture")));
	}

	public static KubeJsLoweringResult unsupported(String reason, String source) {
		if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
		return new KubeJsLoweringResult(Kind.UNSUPPORTED, Optional.empty(), reason, source, Optional.empty());
	}

	private static boolean isMaterializedIdSet(GroupFilter filter) {
		if (filter instanceof GroupFilter.Id) return true;
		if (filter instanceof GroupFilter.Any any) {
			return !any.children().isEmpty()
				&& any.children().stream().allMatch(KubeJsLoweringResult::isMaterializedIdSet);
		}
		return false;
	}
}
