package com.starskyxiii.collapsible_groups.internal.version.data;

import java.util.Objects;

public record ItemDataFormat(
	String schema,
	int schemaVersion,
	String dataFormat,
	String sourceMinecraft
) {
	public ItemDataFormat {
		Objects.requireNonNull(schema, "schema");
		Objects.requireNonNull(dataFormat, "dataFormat");
		Objects.requireNonNull(sourceMinecraft, "sourceMinecraft");
		if (schema.isBlank() || dataFormat.isBlank() || sourceMinecraft.isBlank()) {
			throw new IllegalArgumentException("Item data format identifiers must not be blank");
		}
		if (schemaVersion < 1) {
			throw new IllegalArgumentException("Item data schema version must be positive");
		}
	}
}
