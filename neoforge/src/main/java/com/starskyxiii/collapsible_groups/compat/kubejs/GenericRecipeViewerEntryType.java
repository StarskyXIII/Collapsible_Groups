package com.starskyxiii.collapsible_groups.compat.kubejs;

import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

final class GenericRecipeViewerEntryType extends RecipeViewerEntryType {
	private static final Component<Object> COMPONENT = new Component<>(TypeInfo.of(Object.class),
		new StreamCodec<RegistryFriendlyByteBuf, Object>() {
			@Override
			public Object decode(RegistryFriendlyByteBuf buffer) {
				throw new UnsupportedOperationException("CG generic group entries cannot be deserialized");
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buffer, Object value) {
				throw new UnsupportedOperationException("CG generic group entries cannot be serialized");
			}
		}, Objects::isNull);

	GenericRecipeViewerEntryType(String id) {
		super(id, COMPONENT, COMPONENT, null);
	}

	@Override
	public Object wrapEntry(Context context, Object value) {
		throw unsupportedConversion();
	}

	@Override
	public Object wrapPredicate(Context context, Object value) {
		throw unsupportedConversion();
	}

	private UnsupportedOperationException unsupportedConversion() {
		return new UnsupportedOperationException("Ingredient type '" + id
			+ "' only supports CG generic groupEntries filters, not native entry or predicate conversion");
	}
}
