package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweredGroup;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweringResult;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KubeJsFilterCompilerTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void rejectsNativeFluidStacksRegardlessOfAmountOrComponents() {
		assertNull(KubeJsFilterCompiler.compileFluidFilter(null, new FluidStack(Fluids.WATER, 1000)));
		FluidStack named = new FluidStack(Fluids.WATER, 1);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
		assertNull(KubeJsFilterCompiler.compileFluidFilter(null, named));
	}

	@Test
	void rejectsPartialComponentIngredients() {
		ItemStack item = new ItemStack(Items.STONE);
		item.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
		assertNull(KubeJsFilterCompiler.compileItemFilter(null, (Object) DataComponentIngredient.of(false, item)));

		FluidStack fluid = new FluidStack(Fluids.WATER, 1000);
		fluid.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
		assertNull(KubeJsFilterCompiler.compileFluidFilter(DataComponentFluidIngredient.of(false, fluid)));
	}

	@Test
	void strictComponentIngredientPreservesExactPredicate() {
		ItemStack named = new ItemStack(Items.STONE);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
		Ingredient strict = DataComponentIngredient.of(true, named);
		GroupFilter lowered = KubeJsFilterCompiler.compileItemFilter(null, (Object) strict);
		CompiledFilter compiled = CompiledFilter.compile(lowered);
		ItemStack plain = new ItemStack(Items.STONE);
		ItemStack same = named.copy();
		ItemStack extra = named.copy();
		extra.set(DataComponents.REPAIR_COST, 1);

		assertEquals(strict.test(plain), compiled.matches(new ItemStackIngredientView(plain)));
		assertEquals(strict.test(same), compiled.matches(new ItemStackIngredientView(same)));
		assertEquals(strict.test(extra), compiled.matches(new ItemStackIngredientView(extra)));
		assertTrue(lowered instanceof GroupFilter.ExactStack);
	}

	@Test
	void strictPlainComponentIngredientRemainsExact() {
		Ingredient strict = DataComponentIngredient.of(true, new ItemStack(Items.STONE));
		GroupFilter lowered = KubeJsFilterCompiler.compileItemFilter(null, (Object) strict);
		assertTrue(lowered instanceof GroupFilter.ExactStack);
		CompiledFilter compiled = CompiledFilter.compile(lowered);
		ItemStack named = new ItemStack(Items.STONE);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
		assertTrue(compiled.matches(new ItemStackIngredientView(new ItemStack(Items.STONE))));
		assertFalse(compiled.matches(new ItemStackIngredientView(named)));
	}

	@Test
	void unknownItemObjectKeysRejectTheWholeObject() {
		assertNull(KubeJsFilterCompiler.compileItemFilter(null,
			Map.of("itemId", "minecraft:stone", "count", 4)));
		assertNull(KubeJsFilterCompiler.compileItemFilter(null,
			Map.of("itemId", "minecraft:stone", "itemTag", 5)));
		assertNull(KubeJsFilterCompiler.compileItemFilter(null,
			Map.of("itemId", "minecraft:stone", "itemTag", "not an id")));
	}

	@Test
	void wrapperFailuresAndInvalidGenericIdsBecomeUnsupported() {
		assertNull(KubeJsFilterCompiler.compileItemFilter(null, new Object()));
		assertNull(KubeJsFilterCompiler.compileFluidFilter(null, new Object()));
		assertNull(KubeJsFilterCompiler.compileGenericFilter("example:type", List.of("@example", "not an id")));
		assertNull(KubeJsFilterCompiler.compileGenericFilter("example:type", "#not an id"));
	}

	@Test
	void sizedIngredientsNeverDiscardCountOrAmount() {
		assertNull(KubeJsFilterCompiler.compileItemFilter(null, (Object) SizedIngredient.of(Items.STONE, 1)));
		assertNull(KubeJsFilterCompiler.compileItemFilter(null, (Object) SizedIngredient.of(Items.STONE, 4)));
		assertNull(KubeJsFilterCompiler.compileFluidFilter(null, SizedFluidIngredient.of(Fluids.WATER, 1)));
		assertNull(KubeJsFilterCompiler.compileFluidFilter(null, SizedFluidIngredient.of(Fluids.WATER, 1000)));
	}

	@Test
	void throwingUnknownGroupDoesNotDiscardValidSibling() {
		String source = "client:item";
		JEIGroupEntriesKubeEvent event = new JEIGroupEntriesKubeEvent(List.of(), source,
			new KubeJsMaterializationCapture(source, 1, 1));
		event.group(null, new ItemStack(Items.STONE), ResourceLocation.parse("test:valid"), Component.literal("Valid"));
		event.group(null, new Object(), ResourceLocation.parse("test:invalid"), Component.literal("Invalid"));
		List<KubeJsLoweredGroup> accepted = KubeJSGroupBridge.acceptedGroups(source, event.collectedGroups());
		assertEquals(1, accepted.size());
		assertEquals("__kjs_test_valid", accepted.getFirst().id());
	}

	@Test
	void vanillaIngredientStackComponentsDoNotBecomeExact() {
		ItemStack representative = new ItemStack(Items.STONE);
		representative.set(DataComponents.CUSTOM_NAME, Component.literal("representative"));
		Ingredient ingredient = Ingredient.of(representative);
		assertTrue(ingredient.test(new ItemStack(Items.STONE)));

		GroupFilter lowered = KubeJsFilterCompiler.compileItemFilter(ingredient);
		assertEquals(new GroupFilter.Id("item", "minecraft:stone"), lowered);
	}

	@Test
	void unsupportedGroupIsRemovedFromValidSiblingCandidates() {
		String source = "client:item";
		List<KubeJsLoweredGroup> current = KubeJSGroupBridge.acceptedGroups(source, List.of(
			new KubeJsLoweredGroup("__kjs_old_a", "A", KubeJsLoweringResult.unsupported("count predicate", source)),
			new KubeJsLoweredGroup("__kjs_old_b", "B", KubeJsLoweringResult.exact(Filters.itemId("minecraft:granite"), source))
		));
		assertEquals(List.of("__kjs_old_b"), current.stream().map(KubeJsLoweredGroup::id).toList());
	}
}
