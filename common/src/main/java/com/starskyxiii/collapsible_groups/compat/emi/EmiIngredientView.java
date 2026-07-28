package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;

/** Item-independent views for EMI fluid and generic stack keys. */
final class EmiIngredientView implements IngredientView {
	private final String typeId;
	private final ResourceLocation id;
	private final Object key;

	EmiIngredientView(String typeId, ResourceLocation id, Object key) {
		this.typeId = typeId;
		this.id = id;
		this.key = key;
	}

	@Override public String ingredientType() { return typeId; }
	@Override public ResourceLocation resourceLocation() { return id; }

	@Override
	public boolean hasTag(ResourceLocation tagId) {
		return key instanceof Fluid fluid
			&& fluid.builtInRegistryHolder().is(TagKey.create(Registries.FLUID, tagId));
	}

	@Override public boolean matchesExactStack(String encodedStack) { return false; }
}
