package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Objects;
import java.util.Optional;

public record ItemDataPayload(String dataFormat, JsonElement data) {
	public static final String NBT = "minecraft:nbt";
	public static final String DATA_COMPONENT = "minecraft:data_component";
	public static final String ITEM_COMPONENTS = "minecraft:item_components";

	public ItemDataPayload {
		Objects.requireNonNull(dataFormat, "dataFormat");
		data = Objects.requireNonNull(data, "data").deepCopy();
	}

	@Override public JsonElement data() { return data.deepCopy(); }
	public String encodedValue() { return NBT.equals(dataFormat) && data.isJsonPrimitive() && data.getAsJsonPrimitive().isString() ? data.getAsString() : data.toString(); }
	public static ItemDataPayload nbt(String snbt) { return new ItemDataPayload(NBT, new JsonPrimitive(snbt)); }
	public JsonObject toJson() {
		JsonObject result = new JsonObject();
		result.addProperty("data_format", dataFormat);
		result.add("data", data());
		return result;
	}
	public static Optional<ItemDataPayload> parse(JsonElement value) {
		if (value == null || !value.isJsonObject()) return Optional.empty();
		JsonObject object = value.getAsJsonObject();
		JsonElement format = object.get("data_format");
		if (object.size() != 2 || format == null || !format.isJsonPrimitive()
			|| !format.getAsJsonPrimitive().isString() || !object.has("data")) return Optional.empty();
		return Optional.of(new ItemDataPayload(format.getAsString(), object.get("data")));
	}
}
