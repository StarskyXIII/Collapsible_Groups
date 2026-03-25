package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.compat.jei.api.IngredientTypeRegistry;
import com.starskyxiii.collapsible_groups.core.IngredientView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.resources.ResourceLocation;

/**
 * Wraps an arbitrary JEI ingredient type as an {@link IngredientView} for filter matching.
 */
public final class GenericJeiIngredientView<T> implements IngredientView {
	private final String canonicalTypeId;
	private final T ingredient;
	private final IIngredientHelper<T> helper;

	public GenericJeiIngredientView(String typeId, T ingredient, IIngredientHelper<T> helper) {
		String canonical = IngredientTypeRegistry.getCanonicalId(typeId);
		this.canonicalTypeId = canonical != null ? canonical : typeId;
		this.ingredient = ingredient;
		this.helper = helper;
	}

	@Override
	public String ingredientType() {
		return canonicalTypeId;
	}

	@Override
	public ResourceLocation resourceLocation() {
		ResourceLocation resourceLocation = helper.getResourceLocation(ingredient);
		if (resourceLocation != null) {
			return resourceLocation;
		}
		try {
			Object uid = helper.getUid(ingredient, UidContext.Ingredient);
			if (uid == null) {
				return null;
			}
			return ResourceLocation.tryParse(uid.toString());
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@Override
	public boolean hasTag(ResourceLocation tagId) {
		return helper.getTagStream(ingredient).anyMatch(tagId::equals);
	}

	/** Generic ingredients do not support exact-stack matching; always returns {@code false}. */
	@Override
	public boolean matchesExactStack(String encodedStack) {
		return false;
	}
}
