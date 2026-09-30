package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.ingredient.TagQueryResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;

/** Item-independent views for EMI fluid and generic stack keys. */
final class EmiIngredientView implements IngredientView {
	private final String typeId;
	private final ResourceLocation id;
	private final Object key;
	private final EmiRegistryTagBridge.Tags tags;

	EmiIngredientView(String typeId, ResourceLocation id, Object key) {
		this(typeId, id, key, EmiRegistryTagBridge.Tags.UNPREPARED);
	}

	EmiIngredientView(String typeId, ResourceLocation id, Object key, EmiRegistryTagBridge.Tags tags) {
		this.typeId = typeId;
		this.id = id;
		this.key = key;
		this.tags = tags;
	}

	@Override public String ingredientType() { return typeId; }
	@Override public ResourceLocation resourceLocation() { return id; }

	@Override
	public boolean hasTag(ResourceLocation tagId) {
		return queryTag(tagId) == TagQueryResult.MATCH;
	}

	@Override public TagQueryResult queryTag(ResourceLocation tagId) {
		if (key instanceof Fluid fluid) return fluid.builtInRegistryHolder().is(TagKey.create(Registries.FLUID, tagId))
			? TagQueryResult.MATCH : TagQueryResult.NO_MATCH;
		return tags.query(tagId);
	}

	EmiRegistryTagBridge.Tags tags() { return tags; }

	@Override public boolean matchesExactStack(String encodedStack) { return false; }
}
