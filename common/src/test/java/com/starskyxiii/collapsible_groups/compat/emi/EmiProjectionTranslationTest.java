package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerProjection;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmiProjectionTranslationTest {
	@Test void classifiesHeaderExpandedChildrenAndUngroupedEntries() {
		ViewerIngredient<String> child = ingredient("child");
		ViewerIngredient<String> raw = ingredient("raw");
		GroupDefinition group = new GroupDefinition("group", "Group", true,
			new GroupFilter.Id("item", "minecraft:stone"));
		ViewerProjection<String> projection = new ViewerProjection<>(List.of(
			new ViewerProjection.GroupHeader<>(group, List.of(child), 1, 0, 0, true, List.of(), List.of(child)),
			new ViewerProjection.IngredientEntry<>(raw)
		), Map.of());
		assertEquals(List.of(EmiProjectionTranslation.Kind.HEADER,
			EmiProjectionTranslation.Kind.PROJECTED_CHILD,
			EmiProjectionTranslation.Kind.RAW_INGREDIENT),
			EmiProjectionTranslation.classify(projection).stream().map(EmiProjectionTranslation.Entry::kind).toList());
	}

	private static ViewerIngredient<String> ingredient(String value) {
		return new ViewerIngredient<>(new ViewerIngredientIdentity("item", value), ViewerIngredient.Kind.ITEM,
			value, new IngredientView() {
				@Override public String ingredientType() { return "item"; }
				@Override public ResourceLocation resourceLocation() { return ResourceLocation.parse("minecraft:stone"); }
				@Override public boolean hasTag(ResourceLocation tagId) { return false; }
				@Override public boolean matchesExactStack(String encodedStack) { return false; }
			});
	}
}
