package com.starskyxiii.collapsible_groups.platform;

import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidAmount;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidAmountUnit;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionInput;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionResult;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidIngredient;
import com.starskyxiii.collapsible_groups.platform.services.IPlatformHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.fluids.FluidStack;

import java.nio.file.Path;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String getPlatformName() {

        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return !FMLLoader.isProduction();
    }

    @Override
    public FluidConversionResult convertFluid(FluidConversionInput input) {
        if (!(input.nativeValue() instanceof FluidStack source)) {
            return new FluidConversionResult.Unsupported("NeoForge fluid conversion requires FluidStack");
        }
        if (input.amount().unit() != FluidAmountUnit.MILLIBUCKET) {
            return new FluidConversionResult.Unsupported("NeoForge fluid amount unit must be MILLIBUCKET");
        }
        FluidStack fs = source.copy();
        if (fs.getAmount() != input.amount().value()) {
            return new FluidConversionResult.Unsupported("NeoForge FluidStack amount does not match conversion metadata");
        }
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fs.getFluid());
        IngredientView view = new IngredientView() {
            @Override
            public String ingredientType() {
                return "fluid";
            }

            @Override
            public ResourceLocation resourceLocation() {
                return fluidId;
            }

            @Override
            public boolean hasTag(ResourceLocation tagId) {
                return fs.is(TagKey.create(Registries.FLUID, tagId));
            }

            @Override
            public boolean matchesExactStack(String encodedStack) {
                return false;
            }
        };
        var bucketItem = fs.getFluid().getBucket();
        ItemStack fallback = bucketItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(bucketItem);
        return new FluidConversionResult.Success(new FluidIngredient(fs, input.amount(), fluidId,
            fs.getHoverName(), fallback, view));
    }

    @Override
    public FluidConversionResult convertLegacyFluid(Object value) {
        if (value instanceof FluidStack stack) {
            return convertFluid(new FluidConversionInput(stack,
                new FluidAmount(stack.getAmount(), FluidAmountUnit.MILLIBUCKET)));
        }
        return IPlatformHelper.super.convertLegacyFluid(value);
    }
}
