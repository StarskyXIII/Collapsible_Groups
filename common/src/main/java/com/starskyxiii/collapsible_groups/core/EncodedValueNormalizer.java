package com.starskyxiii.collapsible_groups.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Shared serialized-component value shape used by editor prefill and production matching. */
public final class EncodedValueNormalizer {
	private EncodedValueNormalizer() {}

	/** JSON strings are unwrapped; every other JSON shape keeps its JSON representation. */
	public static String normalize(JsonElement encoded) {
		if (encoded instanceof JsonPrimitive primitive && primitive.isString()) {
			return primitive.getAsString();
		}
		return encoded.toString();
	}
}
