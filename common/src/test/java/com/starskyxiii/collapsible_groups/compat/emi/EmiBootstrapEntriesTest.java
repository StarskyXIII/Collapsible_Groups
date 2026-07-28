package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.viewer.ViewerBootstrapContext;
import com.starskyxiii.collapsible_groups.viewer.ViewerBootstrapEntries;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmiBootstrapEntriesTest {
	@Test void neutralBootstrapAcceptsRealEmiTypedEntriesWithoutCastingTheirPayloadsToJei() {
		ViewerIngredient<EmiIngredient> fluid = new ViewerIngredient<>(
			new ViewerIngredientIdentity("fluid", "water"), ViewerIngredient.Kind.FLUID, emiIngredient(),
			view("fluid", "minecraft:water"));
		ViewerBootstrapContext<EmiIngredient> context = context(List.of(fluid));

		assertEquals(List.of(), ViewerBootstrapEntries.itemStacks(context));
		assertEquals(List.of(ResourceLocation.parse("minecraft:water")),
			ViewerBootstrapEntries.resourceIds(context, ViewerIngredient.Kind.FLUID));
	}

	private static EmiIngredient emiIngredient() {
		return (EmiIngredient) java.lang.reflect.Proxy.newProxyInstance(EmiIngredient.class.getClassLoader(),
			new Class<?>[]{EmiIngredient.class}, (proxy, method, args) -> null);
	}

	private static ViewerBootstrapContext<EmiIngredient> context(List<ViewerIngredient<EmiIngredient>> values) {
		return new ViewerBootstrapContext<>() {
			@Override public List<ViewerIngredientType<EmiIngredient>> ingredientTypes() { return List.of(); }
			@Override public ViewerIngredientUniverse<EmiIngredient> universe() {
				return new ViewerIngredientUniverse<>(values);
			}
		};
	}

	private static IngredientView view(String type, String id) {
		return new IngredientView() {
			@Override public String ingredientType() { return type; }
			@Override public ResourceLocation resourceLocation() { return ResourceLocation.parse(id); }
			@Override public boolean hasTag(ResourceLocation tagId) { return false; }
			@Override public boolean matchesExactStack(String encodedStack) { return false; }
		};
	}
}
