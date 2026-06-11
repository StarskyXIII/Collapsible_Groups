package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PreviewPaneModel(
	String groupId,
	String groupName,
	GroupSource source,
	boolean enabled,
	int itemCount,
	int fluidCount,
	int genericCount,
	List<PreviewIngredientModel> entries
) {
	public PreviewPaneModel {
		groupId = requireNonBlank(groupId, "groupId");
		groupName = requireNonBlank(groupName, "groupName");
		source = source != null ? source : GroupSource.fromGroupId(groupId);
		requireNonNegative(itemCount, "itemCount");
		requireNonNegative(fluidCount, "fluidCount");
		requireNonNegative(genericCount, "genericCount");
		entries = List.copyOf(Objects.requireNonNullElse(entries, List.of()));
	}

	public static PreviewPaneModel empty(String groupId, String groupName, boolean enabled) {
		GroupSource source = GroupSource.fromGroupId(groupId);
		return new PreviewPaneModel(groupId, groupName, source, enabled, 0, 0, 0, List.of());
	}

	public int totalCount() {
		return itemCount + fluidCount + genericCount;
	}

	public int shownEntryCount() {
		return entries.size();
	}

	public int hiddenEntryCount() {
		return Math.max(0, totalCount() - shownEntryCount());
	}

	public List<CountSummaryPart> countSummaryParts() {
		List<CountSummaryPart> parts = new ArrayList<>(3);
		addCount(parts, PreviewIngredientKind.ITEM, itemCount);
		addCount(parts, PreviewIngredientKind.FLUID, fluidCount);
		addCount(parts, PreviewIngredientKind.GENERIC, genericCount);
		return List.copyOf(parts);
	}

	private static void addCount(List<CountSummaryPart> parts, PreviewIngredientKind kind, int count) {
		if (count > 0) {
			parts.add(new CountSummaryPart(kind, count));
		}
	}

	private static void requireNonNegative(int value, String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " must be non-negative");
		}
	}

	private static String requireNonBlank(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value.trim();
	}

	public record CountSummaryPart(PreviewIngredientKind kind, int count) {
		public CountSummaryPart {
			kind = Objects.requireNonNull(kind, "kind");
			requireNonNegative(count, "count");
		}
	}
}
