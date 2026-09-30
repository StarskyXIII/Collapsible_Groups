package com.starskyxiii.collapsible_groups.compat.emi;

import com.google.gson.JsonElement;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class EmiItemOwnership {
	private EmiItemOwnership() {}

	static Map<ItemStack, String> resolve(List<ItemStack> entries,
		ViewerIngredientUniverse<EmiIngredient> universe, Map<ViewerIngredientIdentity, String> owners) {
		Map<ItemStack, String> result = new IdentityHashMap<>();
		for (ItemStack stack : entries) {
			identity(stack).ifPresent(identity -> {
				ViewerIngredient<EmiIngredient> ingredient = universe.byIdentity().get(identity);
				String owner = owners.get(identity);
				if (ingredient != null && ingredient.kind() == ViewerIngredient.Kind.ITEM && owner != null) {
					result.put(stack, owner);
				}
			});
		}
		return Collections.unmodifiableMap(result);
	}

	static Optional<ViewerIngredientIdentity> identity(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return Optional.empty();
		try {
			EmiStack normalized = EmiStack.of(stack).copy().setAmount(1).setChance(1).setRemainder(EmiStack.EMPTY);
			JsonElement serialized = EmiIngredientSerializer.getSerialized(normalized);
			if (serialized == null || serialized.isJsonNull()) return Optional.empty();
			return Optional.of(new ViewerIngredientIdentity("item", EmiIdentityNormalizer.canonicalJson(serialized)));
		} catch (RuntimeException | LinkageError ignored) {
			return Optional.empty();
		}
	}
}
