package com.starskyxiii.collapsible_groups.i18n;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LanguageJson {
    private LanguageJson() {}

    public static Map<String, String> parse(Reader reader, String source) throws IOException {
        try {
            var object = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, String> values = new LinkedHashMap<>();
            for (var entry : object.entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                    throw new IOException("Language entry is not a string: " + source + " / " + entry.getKey());
                }
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
            return values;
        } catch (RuntimeException failure) {
            throw new IOException("Cannot parse language: " + source, failure);
        }
    }
}
