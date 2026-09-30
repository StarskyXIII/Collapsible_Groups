package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiIngredientSerializers;
import dev.emi.emi.stack.serializer.ItemEmiStackSerializer;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmiItemOwnershipTest {
	@Test void copiedComponentsResolveExactlyAndUnknownOrUnserializableItemsStayUnowned() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ItemStack first = new ItemStack(Items.STONE);
		first.set(DataComponents.CUSTOM_NAME, Component.literal("first"));
		ItemStack second = new ItemStack(Items.STONE);
		second.set(DataComponents.CUSTOM_NAME, Component.literal("second"));
		Class<?> type = EmiStack.of(first).getClass();
		var previous = EmiIngredientSerializers.BY_CLASS.put(type, new ItemEmiStackSerializer());
		try {
			var firstId = EmiItemOwnership.identity(first).orElseThrow();
			var secondId = EmiItemOwnership.identity(second).orElseThrow();
			assertNotEquals(firstId, secondId);
			var universe = new ViewerIngredientUniverse<EmiIngredient>(List.of(
				new ViewerIngredient<>(firstId, ViewerIngredient.Kind.ITEM, EmiStack.of(first), new ItemStackIngredientView(first)),
				new ViewerIngredient<>(secondId, ViewerIngredient.Kind.ITEM, EmiStack.of(second), new ItemStackIngredientView(second))));
			ItemStack copied = first.copyWithCount(64);
			ItemStack unknown = new ItemStack(Items.STONE);
			var result = EmiItemOwnership.resolve(List.of(copied, second, unknown), universe,
				Map.of(firstId, "first", secondId, "second"));
			assertEquals("first", result.get(copied));
			assertEquals("second", result.get(second));
			assertFalse(result.containsKey(unknown));
			assertEquals(64, copied.getCount());
			EmiViewerGroupIndex index = new EmiViewerGroupIndex(Runnable::run);
			GroupDefinition low = new GroupDefinition("low", "Low", true, new GroupFilter.Id("item", "minecraft:stone"));
			GroupDefinition high = low.withName("High");
			high = new GroupDefinition("high", "High", true, high.filter()).withPriority(10);
			index.requestRebuild(1, universe, List.of(low, high));
			assertEquals("high", index.resolveItemOwnership(List.of(copied), List.of(low, high)).get(copied));
			assertEquals("low", index.resolveItemOwnership(List.of(copied), List.of(low, high.withEnabled(false))).get(copied));
			assertEquals("low", index.resolveItemOwnership(List.of(copied), List.of(low)).get(copied));
			low = low.withPriority(20);
			index.requestRebuild(1, universe, List.of(low, high));
			assertEquals("low", index.resolveItemOwnership(List.of(copied), List.of(low, high)).get(copied));
			index.reset();
			assertTrue(index.resolveItemOwnership(List.of(copied), List.of(low, high)).isEmpty());
			EmiIngredientSerializers.BY_CLASS.remove(type);
			assertTrue(EmiItemOwnership.resolve(List.of(first), universe, Map.of(firstId, "first")).isEmpty());
		} finally {
			if (previous == null) EmiIngredientSerializers.BY_CLASS.remove(type);
			else EmiIngredientSerializers.BY_CLASS.put(type, previous);
		}
	}
}
