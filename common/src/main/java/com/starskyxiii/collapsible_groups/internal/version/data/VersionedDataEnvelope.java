package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.util.Optional;

public final class VersionedDataEnvelope {
	private static final String MARKER = "$collapsible_groups";
	private static final String SCHEMA = "schema";
	private static final String SCHEMA_VERSION = "schema_version";
	private static final String DATA_FORMAT = "data_format";
	private static final String SOURCE_MINECRAFT = "source_minecraft";
	private static final String DATA = "data";

	private VersionedDataEnvelope() {}

	public enum Support {
		LEGACY,
		CURRENT,
		UNSUPPORTED,
		MALFORMED
	}

	public record Inspection(Support support, Optional<JsonElement> data) {
		public Inspection {
			if (support == null || data == null) throw new NullPointerException();
		}
	}

	public static String wrap(ItemDataFormat format, JsonElement data) {
		JsonObject metadata = new JsonObject();
		metadata.addProperty(SCHEMA, format.schema());
		metadata.addProperty(SCHEMA_VERSION, format.schemaVersion());
		metadata.addProperty(DATA_FORMAT, format.dataFormat());
		metadata.addProperty(SOURCE_MINECRAFT, format.sourceMinecraft());
		metadata.add(DATA, data.deepCopy());
		JsonObject envelope = new JsonObject();
		envelope.add(MARKER, metadata);
		return envelope.toString();
	}

	public static Inspection inspect(String encoded, ItemDataFormat current) {
		final JsonElement parsed;
		try {
			parsed = JsonParser.parseString(encoded);
		} catch (RuntimeException e) {
			return new Inspection(Support.MALFORMED, Optional.empty());
		}
		if (!parsed.isJsonObject()) {
			return new Inspection(Support.LEGACY, Optional.of(parsed));
		}
		JsonObject outer = parsed.getAsJsonObject();
		if (!outer.has(MARKER)) {
			return new Inspection(Support.LEGACY, Optional.of(parsed));
		}
		if (!outer.get(MARKER).isJsonObject()) {
			return new Inspection(Support.MALFORMED, Optional.empty());
		}
		JsonObject object = outer.getAsJsonObject(MARKER);
		if (!hasString(object, SCHEMA)
			|| !hasInteger(object, SCHEMA_VERSION)
			|| !hasString(object, DATA_FORMAT)
			|| !hasString(object, SOURCE_MINECRAFT)
			|| !object.has(DATA)
			|| object.get(DATA).isJsonNull()) {
			return new Inspection(Support.MALFORMED, Optional.empty());
		}
		Integer schemaVersion = exactInteger(object.get(SCHEMA_VERSION));
		boolean supported = schemaVersion != null
			&& current.schema().equals(object.get(SCHEMA).getAsString())
			&& current.schemaVersion() == schemaVersion
			&& current.dataFormat().equals(object.get(DATA_FORMAT).getAsString())
			&& current.sourceMinecraft().equals(object.get(SOURCE_MINECRAFT).getAsString());
		return supported
			? new Inspection(Support.CURRENT, Optional.of(object.get(DATA)))
			: new Inspection(Support.UNSUPPORTED, Optional.empty());
	}

	public static boolean isEnvelope(String encoded) {
		try {
			JsonElement parsed = JsonParser.parseString(encoded);
			if (!parsed.isJsonObject()) return false;
			return parsed.getAsJsonObject().has(MARKER);
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static boolean hasString(JsonObject object, String key) {
		return object.has(key)
			&& object.get(key).isJsonPrimitive()
			&& object.get(key).getAsJsonPrimitive().isString();
	}

	private static boolean hasInteger(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()
			|| !object.get(key).getAsJsonPrimitive().isNumber()) return false;
		return exactInteger(object.get(key)) != null;
	}

	private static Integer exactInteger(JsonElement value) {
		try {
			BigDecimal decimal = value.getAsBigDecimal();
			return decimal.intValueExact();
		} catch (RuntimeException e) {
			return null;
		}
	}
}
