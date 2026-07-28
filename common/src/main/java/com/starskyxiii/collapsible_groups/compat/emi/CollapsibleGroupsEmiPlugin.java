package com.starskyxiii.collapsible_groups.compat.emi;

import com.google.gson.JsonElement;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;

/** Optional EMI entrypoint. Register marks dirty because the final universe is baked afterwards. */
@EmiEntrypoint
public final class CollapsibleGroupsEmiPlugin implements EmiPlugin {
	@Override
	public void initialize(EmiInitRegistry registry) {
		registry.addIngredientSerializer(ProjectedChildEmiIngredient.class,
			new EmiIngredientSerializer<ProjectedChildEmiIngredient>() {
				@Override public String getType() { return "collapsible_groups:projected_child"; }
				@Override public EmiIngredient deserialize(JsonElement element) {
					return EmiIngredientSerializer.getDeserialized(element);
				}
				@Override public JsonElement serialize(ProjectedChildEmiIngredient ingredient) {
					JsonElement serialized = EmiIngredientSerializer.getSerialized(ingredient.delegate());
					return serialized == null ? com.google.gson.JsonNull.INSTANCE : serialized;
				}
			});
	}

	@Override
	public void register(EmiRegistry registry) {
		EmiViewerAdapter.registerRuntime();
		EmiViewerAdapter.instance().markDirty();
	}
}
