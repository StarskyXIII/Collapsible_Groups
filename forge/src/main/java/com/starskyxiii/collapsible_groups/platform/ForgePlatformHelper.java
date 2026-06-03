package com.starskyxiii.collapsible_groups.platform;

import com.starskyxiii.collapsible_groups.core.IngredientView;
import com.starskyxiii.collapsible_groups.platform.services.IPlatformHelper;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fluids.FluidStack;

import java.nio.file.Path;

public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String getPlatformName() {
        return "Forge";
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
    public String getFluidId(Object fluidStack) {
        return BuiltInRegistries.FLUID.getKey(((FluidStack) fluidStack).getFluid()).toString();
    }

    @Override
    public Component getFluidDisplayName(Object fluidStack) {
        return ((FluidStack) fluidStack).getDisplayName();
    }

    @Override
    public ItemStack getFluidFallbackBucket(Object fluidStack) {
        var bucketItem = ((FluidStack) fluidStack).getFluid().getBucket();
        return bucketItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(bucketItem);
    }

    @Override
    public boolean fluidMatchesTag(Object fluidStack, String tagId) {
        return ((FluidStack) fluidStack).getFluid().builtInRegistryHolder().is(
            TagKey.create(Registries.FLUID, ResourceLocation.parse(tagId)));
    }

    @Override
    public IngredientView createFluidView(Object fluidStack) {
        FluidStack fs = (FluidStack) fluidStack;
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fs.getFluid());
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
                return fs.getFluid().builtInRegistryHolder().is(TagKey.create(Registries.FLUID, tagId));
            }

            @Override
            public boolean matchesExactStack(String encodedStack) {
                return false;
            }
        };
    }

    @Override
    public IIngredientType<?> getJeiFluidType() {
        return ForgeTypes.FLUID_STACK;
    }
}
