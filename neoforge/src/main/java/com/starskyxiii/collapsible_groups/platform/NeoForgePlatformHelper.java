package com.starskyxiii.collapsible_groups.platform;

import com.starskyxiii.collapsible_groups.core.IngredientView;
import com.starskyxiii.collapsible_groups.platform.services.IPlatformHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.ModList;
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

        // FMLLoader.isProduction() is no longer static in NeoForge 26.1.1+
        // TODO: update when the new FMLLoader API is confirmed
        return false;
    }

    @Override
    public String getFluidId(Object fluidStack) {
        return BuiltInRegistries.FLUID.getKey(((FluidStack) fluidStack).getFluid()).toString();
    }

    @Override
    public boolean fluidMatchesTag(Object fluidStack, String tagId) {
        return ((FluidStack) fluidStack).is(
            TagKey.create(Registries.FLUID, Identifier.parse(tagId)));
    }

    @Override
    public IngredientView createFluidView(Object fluidStack) {
        FluidStack fs = (FluidStack) fluidStack;
        Identifier fluidId = BuiltInRegistries.FLUID.getKey(fs.getFluid());
        return new IngredientView() {
            @Override
            public String ingredientType() {
                return "fluid";
            }

            @Override
            public Identifier resourceLocation() {
                return fluidId;
            }

            @Override
            public boolean hasTag(Identifier tagId) {
                return fs.is(TagKey.create(Registries.FLUID, tagId));
            }

            @Override
            public boolean matchesExactStack(String encodedStack) {
                return false;
            }
        };
    }

    @Override
    public IIngredientType<?> getJeiFluidType() {
        return NeoForgeTypes.FLUID_STACK;
    }
}
