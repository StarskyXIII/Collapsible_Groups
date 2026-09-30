package com.starskyxiii.collapsible_groups.compat.emi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure identity rules shared by the EMI glue and its viewer-free contract tests. */
public final class EmiIdentityNormalizer {
	private EmiIdentityNormalizer() {}

	public enum StandardKind { ITEM, FLUID, CUSTOM }

	public record Result(String typeId, String valueId, List<String> aliases, boolean serializable) {
		public Result {
			aliases = List.copyOf(aliases);
		}
	}

	public static Result identify(StandardKind kind, JsonElement serialized, String runtimeClass,
		String resourceId, String fallbackExtraData) {
		if (serialized == null || serialized.isJsonNull()) {
			String type = "emi_class:" + runtimeClass;
			String value = resourceId + "|" + (fallbackExtraData == null ? "" : fallbackExtraData);
			return new Result(type, value, List.of(), false);
		}
		String serializerType = serializerType(serialized);
		String typeId = switch (kind) {
			case ITEM -> "item";
			case FLUID -> "fluid";
			case CUSTOM -> "emi:" + serializerType;
		};
		Set<String> aliases = new LinkedHashSet<>();
		if (kind == StandardKind.CUSTOM) {
			aliases.add(serializerType);
			int separator = serializerType.lastIndexOf(':');
			if (separator >= 0 && separator + 1 < serializerType.length()) {
				aliases.add(serializerType.substring(separator + 1));
			}
			aliases.remove(typeId);
		}
		return new Result(typeId, canonicalJson(serialized), List.copyOf(aliases), true);
	}

	public static String serializerType(JsonElement serialized) {
		if (serialized != null && serialized.isJsonObject()) {
			JsonElement type = serialized.getAsJsonObject().get("type");
			if (type != null && type.isJsonPrimitive()) return type.getAsString();
		}
		if (serialized != null && serialized.isJsonArray()) return "list";
		if (serialized != null && serialized.isJsonPrimitive()) {
			String value = serialized.getAsString();
			int separator = value.indexOf(':');
			if (separator > 0) return value.substring(0, separator);
		}
		return "unknown";
	}

	public static String canonicalJson(JsonElement element) {
		if (element == null || element.isJsonNull()) return JsonNull.INSTANCE.toString();
		if (element.isJsonArray()) {
			JsonArray array = new JsonArray();
			for (JsonElement child : element.getAsJsonArray()) array.add(canonicalize(child));
			return array.toString();
		}
		return canonicalize(element).toString();
	}

	private static JsonElement canonicalize(JsonElement element) {
		if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return element;
		if (element.isJsonArray()) {
			JsonArray result = new JsonArray();
			for (JsonElement child : element.getAsJsonArray()) result.add(canonicalize(child));
			return result;
		}
		JsonObject result = new JsonObject();
		List<String> keys = new ArrayList<>(element.getAsJsonObject().keySet());
		keys.sort(Comparator.naturalOrder());
		for (String key : keys) result.add(key, canonicalize(element.getAsJsonObject().get(key)));
		return result;
	}
}
