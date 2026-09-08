package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import dev.latvian.mods.kubejs.platform.IngredientPlatformHelper;
import dev.latvian.mods.kubejs.platform.fabric.ingredient.CustomIngredientWithParent;
import dev.latvian.mods.kubejs.platform.fabric.ingredient.KubeJSNbtIngredient;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricCustomIngredientDetectionTest {
	private static IngredientPlatformHelper ingredientHelper;

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Thread thread = Thread.currentThread();
		ClassLoader previous = thread.getContextClassLoader();
		try {
			thread.setContextClassLoader(IngredientPlatformHelper.class.getClassLoader());
			ingredientHelper = IngredientPlatformHelper.get();
		} finally {
			thread.setContextClassLoader(previous);
		}
	}

	@Test
	void directStackIsExactWhileVanillaIngredientKeepsItsIdOnlySemantics() {
		ItemStack stack = taggedStone();

		GroupFilter exact = KubeJs6FilterCompiler.compileItem(stack);
		GroupFilter vanilla = KubeJs6FilterCompiler.compileItem(Ingredient.of(stack));
		assertInstanceOf(GroupFilter.ExactStack.class, exact);
		assertEquals(new GroupFilter.Id("item", "minecraft:stone"), vanilla);
	}

	@Test
	void nativeStrictNbtIsExactAndNativePartialNbtIsUnsupported() {
		ItemStack stack = taggedStone();

		assertInstanceOf(GroupFilter.ExactStack.class,
			KubeJs6FilterCompiler.compileItem(ingredientHelper.strongNBT(stack)));
		assertNull(KubeJs6FilterCompiler.compileItem(ingredientHelper.weakNBT(stack)));
	}

	@Test
	void nativeStrictNbtWithoutTagStillMeansExactAbsenceOfNbt() {
		assertInstanceOf(GroupFilter.ExactStack.class,
			KubeJs6FilterCompiler.compileItem(ingredientHelper.strongNBT(new ItemStack(Items.STONE))));
	}

	@Test
	void strongNbtWrapperExposesItsActualCustomIngredient() {
		ItemStack stack = taggedStone();

		Ingredient ingredient = ingredientHelper.strongNBT(stack);

		assertEquals("dev.latvian.mods.kubejs.platform.fabric.ingredient.KubeJSNbtIngredient",
			KubeJs6FilterCompiler.customIngredientClass(ingredient));
	}

	@Test
	void strictNbtAroundCustomBaseIsNotNarrowedToItsDisplayedStack() {
		ItemStack stack = taggedStone();
		Ingredient customBase = new CustomIngredientWithParent(Ingredient.of(Items.STONE), ignored -> true).toVanilla();
		Ingredient ingredient = new KubeJSNbtIngredient(customBase, stack.getTag(), true).toVanilla();

		assertNull(KubeJs6FilterCompiler.compileItem(ingredient));
	}

	private static ItemStack taggedStone() {
		ItemStack stack = new ItemStack(Items.STONE);
		CompoundTag tag = new CompoundTag();
		tag.putInt("mode", 1);
		stack.setTag(tag);
		return stack;
	}
}
