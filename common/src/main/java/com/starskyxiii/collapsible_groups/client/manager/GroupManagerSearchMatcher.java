package com.starskyxiii.collapsible_groups.client.manager;


import com.starskyxiii.collapsible_groups.client.manager.model.GroupUiState;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Pure matcher for manager source filtering plus transient search queries. */
public final class GroupManagerSearchMatcher {
	private GroupManagerSearchMatcher() {}

	public record SearchFields(
		String resolvedDisplayName,
		String fallbackDisplayName,
		String groupId,
		GroupSource source,
		String localizedSourceLabel
	) {
		public SearchFields {
			resolvedDisplayName = clean(resolvedDisplayName);
			fallbackDisplayName = clean(fallbackDisplayName);
			groupId = clean(groupId);
			source = Objects.requireNonNull(source, "source");
			localizedSourceLabel = clean(localizedSourceLabel);
		}
	}

	public static boolean matches(GroupUiState.ManagerSourceFilter sourceFilter, String query, SearchFields fields) {
		Objects.requireNonNull(fields, "fields");
		return matchesSource(sourceFilter, fields.source()) && matchesQuery(query, fields);
	}

	public static boolean matchesSource(GroupUiState.ManagerSourceFilter sourceFilter, GroupSource source) {
		Objects.requireNonNull(source, "source");
		GroupUiState.ManagerSourceFilter filter = sourceFilter == null
			? GroupUiState.ManagerSourceFilter.ALL
			: sourceFilter;
		return switch (filter) {
			case ALL -> true;
			case USER -> source == GroupSource.USER;
			case BUILTIN -> source == GroupSource.BUILTIN;
			case KUBEJS -> source == GroupSource.KUBEJS;
		};
	}

	public static boolean matchesQuery(String query, SearchFields fields) {
		Objects.requireNonNull(fields, "fields");
		List<String> tokens = tokens(query);
		if (tokens.isEmpty()) return true;
		List<String> searchable = searchableFields(fields);
		for (String token : tokens) {
			boolean matched = false;
			for (String field : searchable) {
				if (field.contains(token)) {
					matched = true;
					break;
				}
			}
			if (!matched) return false;
		}
		return true;
	}

	private static List<String> searchableFields(SearchFields fields) {
		List<String> values = new ArrayList<>();
		addNormalized(values, fields.resolvedDisplayName());
		addNormalized(values, fields.fallbackDisplayName());
		addNormalized(values, fields.groupId());
		addNormalized(values, fields.localizedSourceLabel());
		for (String alias : sourceAliases(fields.source())) {
			addNormalized(values, alias);
		}
		return values;
	}

	private static List<String> tokens(String query) {
		String normalized = normalize(query);
		if (normalized.isEmpty()) return List.of();
		return List.of(normalized.split("\\s+"));
	}

	private static List<String> sourceAliases(GroupSource source) {
		return switch (source) {
			case USER -> List.of("user", "custom");
			case BUILTIN -> List.of("builtin", "built-in", "built in", "default");
			case KUBEJS -> List.of("kubejs", "kube js");
		};
	}

	private static void addNormalized(List<String> values, String value) {
		String normalized = normalize(value);
		if (!normalized.isEmpty()) {
			values.add(normalized);
		}
	}

	private static String normalize(String value) {
		return clean(value).toLowerCase(Locale.ROOT);
	}

	private static String clean(String value) {
		return value == null ? "" : value.trim();
	}
}
