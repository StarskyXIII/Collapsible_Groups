package com.starskyxiii.collapsible_groups.platform;

import com.starskyxiii.collapsible_groups.Constants;
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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fluids.FluidStack;

import java.nio.file.Path;

public class ForgePlatformHelper implements IPlatformHelper {
    @Override
    public java.util.List<net.minecraft.server.packs.PackResources> languageResourcePacks(
        net.minecraft.server.packs.resources.ResourceManager manager) {
        java.util.List<net.minecraft.server.packs.PackResources> result = new java.util.ArrayList<>();
        try (var packs = manager.listPacks()) {
            packs.forEach(pack -> {
                var children = pack.getChildren();
                if (children == null) {
                    result.add(pack);
                } else {
                    var ordered = new java.util.ArrayList<>(children);
                    java.util.Collections.reverse(ordered);
                    result.addAll(ordered);
                }
            });
        }
        return java.util.List.copyOf(result);
    }


    @Override
    public java.util.List<net.minecraft.server.packs.PackResources> groupResourcePacks(
        net.minecraft.server.packs.resources.ResourceManager manager) {
        return groupResourcePacks(manager, net.minecraftforge.resource.ResourcePackLoader.getPackFor(Constants.MOD_ID).orElse(null));
    }

    static java.util.List<net.minecraft.server.packs.PackResources> groupResourcePacks(
        net.minecraft.server.packs.resources.ResourceManager manager, net.minecraft.server.packs.PackResources bundledPack) {
        java.util.List<net.minecraft.server.packs.PackResources> result = new java.util.ArrayList<>();
        try (var packs = manager.listPacks()) { packs.forEach(pack -> appendGroupPack(result, pack, bundledPack)); }
        return java.util.List.copyOf(result);
    }

    private static void appendGroupPack(java.util.List<net.minecraft.server.packs.PackResources> result,
        net.minecraft.server.packs.PackResources pack, net.minecraft.server.packs.PackResources bundledPack) {
        if (pack == bundledPack || (pack instanceof net.minecraftforge.resource.PathPackResources pathPack
            && bundledPack instanceof net.minecraftforge.resource.PathPackResources bundledPath
            && pathPack.getSource().equals(bundledPath.getSource()))) return;
        if (pack instanceof net.minecraftforge.resource.DelegatingPackResources delegated) {
            var children = new java.util.ArrayList<>(delegated.getChildren());
            java.util.Collections.reverse(children);
            children.forEach(child -> appendGroupPack(result, child, bundledPack));
        } else {
            result.add(pack);
        }
    }

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
    public FluidConversionResult convertFluid(FluidConversionInput input) {
        if (!(input.nativeValue() instanceof FluidStack source)) {
            return new FluidConversionResult.Unsupported("Forge fluid conversion requires FluidStack");
        }
        if (input.amount().unit() != FluidAmountUnit.MILLIBUCKET) {
            return new FluidConversionResult.Unsupported("Forge fluid amount unit must be MILLIBUCKET");
        }
        FluidStack fs = source.copy();
        if (fs.getAmount() != input.amount().value()) {
            return new FluidConversionResult.Unsupported("Forge FluidStack amount does not match conversion metadata");
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
                return fs.getFluid().builtInRegistryHolder().is(TagKey.create(Registries.FLUID, tagId));
            }

            @Override
            public boolean matchesExactStack(String encodedStack) {
                return false;
            }
        };
        var bucketItem = fs.getFluid().getBucket();
        ItemStack fallback = bucketItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(bucketItem);
        return new FluidConversionResult.Success(new FluidIngredient(fs, input.amount(), fluidId,
            fs.getDisplayName(), fallback, view));
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
