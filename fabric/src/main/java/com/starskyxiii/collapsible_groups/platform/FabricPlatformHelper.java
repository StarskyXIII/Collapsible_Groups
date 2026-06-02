package com.starskyxiii.collapsible_groups.platform;

import com.starskyxiii.collapsible_groups.core.IngredientView;
import com.starskyxiii.collapsible_groups.platform.services.IPlatformHelper;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.ingredients.IIngredientType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

import java.nio.file.Path;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public String getFluidId(Object fluidStack) {
        IJeiFluidIngredient ingredient = (IJeiFluidIngredient) fluidStack;
        return BuiltInRegistries.FLUID.getKey(ingredient.getFluidVariant().getFluid()).toString();
    }

    @Override
    public boolean fluidMatchesTag(Object fluidStack, String tagId) {
        IJeiFluidIngredient ingredient = (IJeiFluidIngredient) fluidStack;
        return ingredient.getFluidVariant().getFluid().builtInRegistryHolder().is(
            TagKey.create(Registries.FLUID, ResourceLocation.parse(tagId)));
    }

    @Override
    public IngredientView createFluidView(Object fluidStack) {
        IJeiFluidIngredient ingredient = (IJeiFluidIngredient) fluidStack;
        FluidVariant variant = ingredient.getFluidVariant();
        Fluid fluid = variant.getFluid();
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        return new IngredientView() {
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
                return fluid.builtInRegistryHolder().is(TagKey.create(Registries.FLUID, tagId));
            }

            @Override
            public boolean matchesExactStack(String encodedStack) {
                return false;
            }
        };
    }

    @Override
    public IIngredientType<?> getJeiFluidType() {
        return FabricTypes.FLUID_STACK;
    }
}
