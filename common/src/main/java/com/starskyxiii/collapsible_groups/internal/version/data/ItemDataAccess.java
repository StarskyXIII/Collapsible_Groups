package com.starskyxiii.collapsible_groups.internal.version.data;

import java.util.List;
import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter.Evaluation;

public interface ItemDataAccess<S, V> {
	ExactStackCodec<S> exactStacks();

	boolean matchesDataValue(S stack, String dataTypeId, String encodedValue);

	boolean matchesDataPath(S stack, String dataTypeId, String path, String expectedValue);

    default Evaluation evaluateDataValue(S stack, String dataTypeId, String legacyValue, ItemDataPayload payload) {
        if (payload != null) return Evaluation.UNAVAILABLE;
        return matchesDataValue(stack, dataTypeId, legacyValue) ? Evaluation.MATCH : Evaluation.NO_MATCH;
    }

    default Evaluation evaluateDataPath(S stack, String dataTypeId, String path, String legacyValue, ItemDataPayload payload) {
        if (payload != null) return Evaluation.UNAVAILABLE;
        return matchesDataPath(stack, dataTypeId, path, legacyValue) ? Evaluation.MATCH : Evaluation.NO_MATCH;
    }

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
