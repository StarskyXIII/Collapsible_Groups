package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ViewerIngredientIdentityTest {
	@Test
	void runtimeKeyControlsEqualityWithoutChangingPersistentValue() {
		CollisionKey firstKey = new CollisionKey("first");
		CollisionKey secondKey = new CollisionKey("second");
		ViewerIngredientIdentity first = new ViewerIngredientIdentity("item", "same-string", firstKey);
		ViewerIngredientIdentity second = new ViewerIngredientIdentity("item", "same-string", secondKey);
		ViewerIngredientIdentity equivalent = new ViewerIngredientIdentity("item", "different-string",
			new CollisionKey("first"));

		assertNotEquals(first, second);
		assertEquals(first, equivalent);
		assertEquals("same-string", first.valueId());
		assertEquals(new ViewerIngredientIdentity("item", "legacy"),
			new ViewerIngredientIdentity("item", "legacy"));
	}

	@Test
	void universeKeepsCollidingRuntimeKeysAndCanonicalizesTrueDuplicates() {
		ViewerIngredient<String> first = ingredient("first", new CollisionKey("first"));
		ViewerIngredient<String> second = ingredient("second", new CollisionKey("second"));
		ViewerIngredient<String> duplicateFirst = ingredient("duplicate-first", new CollisionKey("first"));

		ViewerIngredientUniverse<String> universe =
			new ViewerIngredientUniverse<>(List.of(first, second, duplicateFirst));

		assertEquals(List.of(first, second), universe.ordered());
		assertEquals(2, universe.byIdentity().size());
		assertSame(first, universe.byIdentity().get(duplicateFirst.identity()));
	}

	private static ViewerIngredient<String> ingredient(String entry, CollisionKey key) {
		return new ViewerIngredient<>(new ViewerIngredientIdentity("item", "same-string", key),
			ViewerIngredient.Kind.ITEM, entry, new IngredientView() {
				@Override public String ingredientType() { return "item"; }
				@Override public ResourceLocation resourceLocation() { return ResourceLocation.parse("test:item"); }
				@Override public boolean hasTag(ResourceLocation tagId) { return false; }
				@Override public boolean matchesExactStack(String encodedStack) { return false; }
			});
	}

	private record CollisionKey(String id) {
		@Override public String toString() { return "same-string"; }
	}
}
