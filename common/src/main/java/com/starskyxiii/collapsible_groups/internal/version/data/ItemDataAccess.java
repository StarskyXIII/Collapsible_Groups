package com.starskyxiii.collapsible_groups.internal.version.data;

import java.util.List;

public interface ItemDataAccess<S, V> {
	ExactStackCodec<S> exactStacks();

	boolean matchesDataValue(S stack, String dataTypeId, String encodedValue);

	boolean matchesDataPath(S stack, String dataTypeId, String path, String expectedValue);

	List<DataReference<V>> enumerateData(S stack);

	List<DataPath<V>> enumeratePaths(V root);

	record DataReference<V>(
		String dataTypeId,
		V encodedValueNode,
		String encodedValue,
		boolean fromPatch
	) {}

	record DataPath<V>(String path, V value) {}
}
