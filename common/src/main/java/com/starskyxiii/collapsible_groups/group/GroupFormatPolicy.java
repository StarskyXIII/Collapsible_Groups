package com.starskyxiii.collapsible_groups.group;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;

public final class GroupFormatPolicy {
	private GroupFormatPolicy() {}
	public static ItemDataPayload payload(GroupFilter filter) {
		if (filter instanceof GroupFilter.ExactStack value) return value.payload();
		if (filter instanceof GroupFilter.Nbt value) return value.payload();
		if (filter instanceof GroupFilter.NbtPath value) return value.payload();
		if (filter instanceof GroupFilter.HasComponent value) return value.payload();
		if (filter instanceof GroupFilter.ComponentPath value) return value.payload();
		return null;
	}
	public static boolean nativePayloadSupported(GroupFilter filter) {
		ItemDataPayload payload = payload(filter);
		if (payload == null) return true;
		return ItemDataPayload.NBT.equals(payload.dataFormat()) && payload.data().isJsonPrimitive()
			&& payload.data().getAsJsonPrimitive().isString()
			&& !(filter instanceof GroupFilter.HasComponent) && !(filter instanceof GroupFilter.ComponentPath);
	}

	public static GroupDocumentFormat inferredFormat(GroupFilter filter) {
		return containsTyped(filter) ? GroupDocumentFormat.V1 : GroupDocumentFormat.LEGACY;
	}
	private static boolean containsTyped(GroupFilter filter) {
		if (filter instanceof GroupFilter.Any value) return value.children().stream().anyMatch(GroupFormatPolicy::containsTyped);
		if (filter instanceof GroupFilter.All value) return value.children().stream().anyMatch(GroupFormatPolicy::containsTyped);
		if (filter instanceof GroupFilter.Not value) return containsTyped(value.child());
		return payload(filter) != null;
	}
	public static boolean representable(GroupDocumentFormat format, GroupFilter filter) {
		if (format == GroupDocumentFormat.UNSUPPORTED) return false;
		if (filter instanceof GroupFilter.Any value) return value.children().stream().allMatch(child -> representable(format, child));
		if (filter instanceof GroupFilter.All value) return value.children().stream().allMatch(child -> representable(format, child));
		if (filter instanceof GroupFilter.Not value) return representable(format, value.child());
		if (filter instanceof GroupFilter.Unsupported) return true;
		if (filter instanceof GroupFilter.ExactStack || filter instanceof GroupFilter.Nbt || filter instanceof GroupFilter.NbtPath)
			return format == GroupDocumentFormat.V1;
		if (filter instanceof GroupFilter.HasComponent || filter instanceof GroupFilter.ComponentPath)
			return (format == GroupDocumentFormat.V1) == (payload(filter) != null);
		return true;
	}
	public static GroupFilter editorFilter(GroupDocumentFormat format, GroupFilter filter) {
		if (format != GroupDocumentFormat.V1) return filter;
		if (filter instanceof GroupFilter.Any value) return new GroupFilter.Any(value.children().stream().map(child -> editorFilter(format, child)).toList());
		if (filter instanceof GroupFilter.All value) return new GroupFilter.All(value.children().stream().map(child -> editorFilter(format, child)).toList());
		if (filter instanceof GroupFilter.Not value) return new GroupFilter.Not(editorFilter(format, value.child()));
		if (payload(filter) != null) return filter;
		if (filter instanceof GroupFilter.ExactStack value) return new GroupFilter.ExactStack(ItemDataPayload.nbt(value.encodedStack()));
		if (filter instanceof GroupFilter.Nbt value) return new GroupFilter.Nbt(ItemDataPayload.nbt(value.expectedSnbt()));
		if (filter instanceof GroupFilter.NbtPath value) return new GroupFilter.NbtPath(value.path(), ItemDataPayload.nbt(value.expectedSnbt()));
		return filter;
	}

	public static boolean writable(GroupDefinition group) { return representable(group.documentFormat(), group.filter()); }
}
