package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.io.IOException;
import java.util.Objects;

public record ItemDataPayload(String dataFormat, JsonElement data) {
    public static final String DATA_COMPONENT = "minecraft:data_component";
    public static final String ITEM_COMPONENTS = "minecraft:item_components";
    public static final String NBT = "minecraft:nbt";

    public ItemDataPayload {
        Objects.requireNonNull(dataFormat, "dataFormat");
        data = Objects.requireNonNull(data, "data").deepCopy();
        parseLiteral(data.toString());
    }

    @Override public JsonElement data() { return data.deepCopy(); }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("data_format", dataFormat);
        object.add("data", data());
        return object;
    }

    public static ItemDataPayload fromJson(JsonElement element, String expectedFormat) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException("Expected a data payload object");
        JsonObject object = element.getAsJsonObject();
        JsonElement format = object.get("data_format");
        if (format == null || !format.isJsonPrimitive() || !format.getAsJsonPrimitive().isString()
            || !expectedFormat.equals(format.getAsString()) || !object.has("data") || object.size() != 2) {
            throw new IllegalArgumentException("Unsupported data payload");
        }
        return new ItemDataPayload(expectedFormat, object.get("data"));
    }

    public static JsonElement parseLiteral(String literal) {
        validateLexemes(literal);
        try (JsonReader reader = new JsonReader(new StringReader(literal))) {
            reader.setLenient(false);
            if (reader.peek() == JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Expected a JSON literal");
            JsonElement result = Streams.parse(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Expected one complete JSON literal");
            return result;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Invalid JSON literal", exception);
        }
    }

    private static void validateLexemes(String literal) {
        for (int index = 0; index < literal.length();) {
            char current = literal.charAt(index);
            if (" \r\n\t{}[],:".indexOf(current) >= 0) {
                index++;
            } else if (current == '"') {
                boolean closed = false;
                for (index++; index < literal.length(); index++) {
                    char character = literal.charAt(index);
                    if (character == '"') { index++; closed = true; break; }
                    if (character < 0x20) throw new IllegalArgumentException("Unescaped control character in JSON string");
                    if (character == '\\') {
                        if (++index >= literal.length()) throw new IllegalArgumentException("Incomplete JSON escape");
                        char escape = literal.charAt(index);
                        if (escape == 'u') {
                            for (int digit = 0; digit < 4; digit++) {
                                if (++index >= literal.length() || Character.digit(literal.charAt(index), 16) < 0) {
                                    throw new IllegalArgumentException("Invalid JSON Unicode escape");
                                }
                            }
                        } else if ("\"\\/bfnrt".indexOf(escape) < 0) throw new IllegalArgumentException("Invalid JSON escape");
                    }
                }
                if (!closed) throw new IllegalArgumentException("Unterminated JSON string");
            } else {
                int start = index;
                while (index < literal.length() && " \r\n\t{}[],:\"".indexOf(literal.charAt(index)) < 0) index++;
                String token = literal.substring(start, index);
                if (!token.equals("true") && !token.equals("false") && !token.equals("null")
                    && !token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
                    throw new IllegalArgumentException("Invalid JSON literal token");
                }
            }
        }
    }
}
