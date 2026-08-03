package com.starskyxiii.collapsible_groups.viewer;

import java.util.Objects;

/** Viewer-neutral identity with separate runtime equality and persistent display values. */
public final class ViewerIngredientIdentity {
	private final String typeId;
	private final String valueId;
	private final Object runtimeKey;

	public ViewerIngredientIdentity(String typeId, String valueId) {
		this(typeId, valueId, valueId);
	}

	public ViewerIngredientIdentity(String typeId, String valueId, Object runtimeKey) {
		this.typeId = Objects.requireNonNull(typeId, "typeId");
		this.valueId = Objects.requireNonNull(valueId, "valueId");
		this.runtimeKey = Objects.requireNonNull(runtimeKey, "runtimeKey");
		if (typeId.isBlank()) throw new IllegalArgumentException("typeId must not be blank");
		if (valueId.isBlank()) throw new IllegalArgumentException("valueId must not be blank");
	}

	public String typeId() {
		return typeId;
	}

	public String valueId() {
		return valueId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof ViewerIngredientIdentity identity)) return false;
		return typeId.equals(identity.typeId) && runtimeKey.equals(identity.runtimeKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(typeId, runtimeKey);
	}

	@Override
	public String toString() {
		return "ViewerIngredientIdentity[typeId=" + typeId + ", valueId=" + valueId + ']';
	}
}
