package com.starskyxiii.collapsible_groups.platform.fluid;

import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.platform.TestPlatformHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FluidConversionContractTest {
	@Test void amountRequiresUnitAndNonNegativeValue() {
		assertThrows(IllegalArgumentException.class,
			() -> new FluidAmount(-1, FluidAmountUnit.MILLIBUCKET));
		assertThrows(NullPointerException.class, () -> new FluidAmount(1, null));
	}

	@Test void unsupportedBackendReturnsReasonInsteadOfCasting() {
		var platform = new TestPlatformHelper();
		var input = new FluidConversionInput(new Object(),
			new FluidAmount(1000, FluidAmountUnit.MILLIBUCKET));
		var unsupported = assertInstanceOf(FluidConversionResult.Unsupported.class,
			platform.convertFluid(input));
		assertEquals("Common Test does not support native fluid conversion", unsupported.reason());
		assertEquals(unsupported.reason(), assertThrows(IllegalArgumentException.class,
			() -> unsupported.require()).getMessage());
	}

	@Test void successfulConversionKeepsNativeDataAmountUnitAndViewTogether() {
		Object nativeData = new Object();
		FluidAmount amount = new FluidAmount(81000, FluidAmountUnit.FABRIC_TRANSFER);
		IngredientView view = view();
		FluidIngredient ingredient = new FluidIngredient(nativeData, amount,
			ResourceLocation.parse("minecraft:water"), Component.literal("Water"), ItemStack.EMPTY, view);
		FluidConversionResult result = new FluidConversionResult.Success(ingredient);

		assertSame(nativeData, result.require().nativeValue());
		assertSame(amount, result.require().amount());
		assertSame(view, result.require().view());
		assertSame(ingredient, new TestPlatformHelper().convertLegacyFluid(ingredient).require());
	}

	private static IngredientView view() {
		return new IngredientView() {
			@Override public String ingredientType() { return "fluid"; }
			@Override public ResourceLocation resourceLocation() {
				return ResourceLocation.parse("minecraft:water");
			}
			@Override public boolean hasTag(ResourceLocation tagId) { return false; }
			@Override public boolean matchesExactStack(String encodedStack) { return false; }
		};
	}
}
