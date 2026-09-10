package com.starskyxiii.collapsible_groups.group;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public enum GroupDocumentFormat {
    LEGACY, V1, UNSUPPORTED;

    public static GroupDocumentFormat inspect(JsonObject document) {
        if (!document.has("schema_version")) return LEGACY;
        JsonElement version = document.get("schema_version");
        if (version.isJsonPrimitive() && version.getAsJsonPrimitive().isNumber()) {
            try {
                if (version.getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0) return V1;
            } catch (NumberFormatException ignored) {
                return UNSUPPORTED;
            }
        }
        return UNSUPPORTED;
    }
}
