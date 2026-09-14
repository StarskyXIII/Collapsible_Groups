package com.starskyxiii.collapsible_groups.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.persistence.AtomicFileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonSettingsStorage implements SettingsController.Storage {
    private final Path path;

    public JsonSettingsStorage(Path path) { this.path = path; }

    private JsonObject document() throws Exception {
        if (!Files.exists(path)) return new JsonObject();
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static SettingsSnapshot parse(JsonObject document) {
        return SettingsSnapshot.read(key -> {
            String[] parts = key.split("\\.");
            if (!document.has(parts[0])) return null;
            var section = document.getAsJsonObject(parts[0]);
            if (!section.has(parts[1])) return null;
            var value = section.get(parts[1]).getAsJsonPrimitive();
            if (value.isBoolean()) return value.getAsBoolean();
            if (value.isNumber()) return value.getAsNumber();
            return value.getAsString();
        });
    }

    @Override public SettingsSnapshot read() throws Exception {
        if (!Files.exists(path)) write(SettingsSnapshot.DEFAULTS);
        return parse(document());
    }

    @Override public void write(SettingsSnapshot settings) throws Exception {
        JsonObject document = document();
        parse(document);
        settings.write((key, value) -> {
            String[] parts = key.split("\\.");
            if (!document.has(parts[0])) document.add(parts[0], new JsonObject());
            JsonObject section = document.getAsJsonObject(parts[0]);
            if (value instanceof Boolean bool) section.addProperty(parts[1], bool);
            else if (value instanceof Number number) section.addProperty(parts[1], number);
            else section.addProperty(parts[1], (String) value);
        });
        AtomicFileWriter.write(path, new GsonBuilder().setPrettyPrinting().create().toJson(document) + "\n");
    }
}
