package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewerHeaderIconResolverTest {
	@Test
	void indexedLookupNeverReadsCandidateViewsAfterConstruction() {
		var reads = new java.util.concurrent.atomic.AtomicInteger();
		var entries = java.util.stream.IntStream.range(0, 6166).mapToObj(i ->
			new ViewerIngredient<>(new ViewerIngredientIdentity("item", "variant:" + i),
				ViewerIngredient.Kind.ITEM, i, new IngredientView() {
					@Override public String ingredientType() { return "item"; }
					@Override public ResourceLocation resourceLocation() {
						reads.incrementAndGet();
						return ResourceLocation.parse("ae2:facade");
					}
					@Override public boolean hasTag(ResourceLocation tag) { return false; }
					@Override public boolean matchesExactStack(String stack) { return false; }
				})).toList();
		var universe = new ViewerIngredientUniverse<>(entries);
		ViewerHeaderIconResolver.find(GroupIconDefinition.item("ae2:facade"), universe);
		int initial = reads.get();
		for (int i = 0; i < 100; i++) {
			assertEquals(entries.get(0), ViewerHeaderIconResolver.find(GroupIconDefinition.item("ae2:facade"), universe));
		}
		assertEquals(initial, reads.get());
	}

	@Test
	void fallbackIsLazyAndSkipsRepeatedResolvedIdentities() {
		var first = ingredient("item", "variant:1", "ae2:facade");
		var second = ingredient("item", "variant:2", "minecraft:stone");
		var universe = new ViewerIngredientUniverse<>(List.of(first, second));
		Iterable<GroupIconDefinition> forbidden = () -> { throw new AssertionError("fallback evaluated"); };
		assertEquals(List.of(first, second), ViewerHeaderIconResolver.resolveDefinitions(
			List.of(GroupIconDefinition.item("ae2:facade"), GroupIconDefinition.item("minecraft:stone")), forbidden, universe));
		var fallback = new java.util.ArrayList<GroupIconDefinition>(java.util.Collections.nCopies(6166,
			GroupIconDefinition.item("ae2:facade")));
		fallback.add(GroupIconDefinition.item("minecraft:stone"));
		assertEquals(List.of(first, second), ViewerHeaderIconResolver.resolveDefinitions(List.of(), fallback, universe));
	}

	@Test
	void persistentValueLookupDoesNotUseRuntimeIdentityEquality() {
		var first = new ViewerIngredient<>(new ViewerIngredientIdentity("item", "same:value", "runtime:1"),
			ViewerIngredient.Kind.ITEM, "first", new FakeView("item", ResourceLocation.parse("test:first")));
		var second = new ViewerIngredient<>(new ViewerIngredientIdentity("item", "same:value", "runtime:2"),
			ViewerIngredient.Kind.ITEM, "second", new FakeView("item", ResourceLocation.parse("test:second")));
		assertEquals(first, ViewerHeaderIconResolver.find(GroupIconDefinition.item("same:value"),
			new ViewerIngredientUniverse<>(List.of(first, second))));
	}
	@Test
	void exactIdentityOutranksEarlierResourceLocationFallback() {
		ViewerIngredient<String> resourceMatch = ingredient("item", "serialized:first", "minecraft:oak_planks");
		ViewerIngredient<String> exactMatch = ingredient("item", "minecraft:oak_planks", "minecraft:other");
		var result = ViewerHeaderIconResolver.resolve(List.of(GroupIconDefinition.item("minecraft:oak_planks")),
			List.of(), new ViewerIngredientUniverse<>(List.of(resourceMatch, exactMatch)));
		assertEquals(List.of(exactMatch), result);
	}

	@Test
	void resolvesSerializedEntriesByResourceLocationAndFillsPartialFallbackWithoutDuplicates() {
		ViewerIngredient<String> planks = ingredient("item", "{item:planks}", "minecraft:oak_planks");
		ViewerIngredient<String> log = ingredient("item", "{item:log}", "minecraft:oak_log");
		ViewerIngredient<String> stone = ingredient("item", "{item:stone}", "minecraft:stone");
		var result = ViewerHeaderIconResolver.resolve(
			List.of(GroupIconDefinition.item("minecraft:oak_planks"), GroupIconDefinition.item("missing:item")),
			List.of(planks, log, stone), new ViewerIngredientUniverse<>(List.of(planks, log, stone)));
		assertEquals(List.of(planks, log), result);
	}

	@Test
	void respectsIngredientTypeAndCurrentUniverse() {
		ViewerIngredient<String> fluid = ingredient("fluid", "{fluid:water}", "minecraft:water");
		ViewerIngredient<String> replacement = ingredient("item", "{item:water}", "minecraft:water");
		var result = ViewerHeaderIconResolver.resolve(List.of(GroupIconDefinition.item("minecraft:water")),
			List.of(), new ViewerIngredientUniverse<>(List.of(fluid, replacement)));
		assertEquals(List.of(replacement), result);
	}

	@Test
	void resolvesEveryCallAgainstTheProvidedUniverseWithoutStaleCache() {
		GroupIconDefinition icon = GroupIconDefinition.item("minecraft:oak_planks");
		ViewerIngredient<String> oldValue = ingredient("item", "{generation:old}", "minecraft:oak_planks");
		ViewerIngredient<String> newValue = ingredient("item", "{generation:new}", "minecraft:oak_planks");
		assertEquals(List.of(oldValue), ViewerHeaderIconResolver.resolve(List.of(icon), List.of(),
			new ViewerIngredientUniverse<>(List.of(oldValue))));
		assertEquals(List.of(newValue), ViewerHeaderIconResolver.resolve(List.of(icon), List.of(),
			new ViewerIngredientUniverse<>(List.of(newValue))));
	}

	private static ViewerIngredient<String> ingredient(String type, String value, String resourceId) {
		return new ViewerIngredient<>(new ViewerIngredientIdentity(type, value), ViewerIngredient.Kind.ITEM,
			value, new FakeView(type, ResourceLocation.parse(resourceId)));
	}

	private record FakeView(String ingredientType, ResourceLocation resourceLocation) implements IngredientView {
		@Override public boolean hasTag(ResourceLocation tagId) { return false; }
		@Override public boolean matchesExactStack(String encodedStack) { return false; }
	}
}
