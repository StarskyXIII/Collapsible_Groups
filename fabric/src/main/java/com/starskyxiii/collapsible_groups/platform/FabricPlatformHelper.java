package com.starskyxiii.collapsible_groups.platform;

import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.mixin.MixinGroupResourcePackAccessor;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidAmountUnit;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionInput;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionResult;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidIngredient;
import com.starskyxiii.collapsible_groups.platform.services.IPlatformHelper;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.impl.resource.loader.GroupResourcePack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public List<PackResources> languageResourcePacks(ResourceManager manager) {
        List<PackResources> result = new ArrayList<>();
        try (var packs = manager.listPacks()) {
            packs.forEach(pack -> {
                if (pack instanceof GroupResourcePack) {
                    result.addAll(((MixinGroupResourcePackAccessor) pack).cg$getPacks());
                } else {
                    result.add(pack);
                }
            });
        }
        return List.copyOf(result);
    }


    @Override
    public List<PackResources> groupResourcePacks(ResourceManager manager) {
        List<PackResources> result = new ArrayList<>();
        try (var packs = manager.listPacks()) { packs.forEach(pack -> appendGroupPack(result, pack)); }
        return List.copyOf(result);
    }

    private static void appendGroupPack(List<PackResources> result, PackResources pack) {
        if (pack instanceof GroupResourcePack) {
            ((MixinGroupResourcePackAccessor) pack).cg$getPacks().forEach(child -> appendGroupPack(result, child));
        } else {
            result.add(pack);
        }
    }

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
    public FluidConversionResult convertFluid(FluidConversionInput input) {
        if (!(input.nativeValue() instanceof FluidVariant variant)) {
            return new FluidConversionResult.Unsupported("Fabric fluid conversion requires FluidVariant");
        }
        if (input.amount().unit() != FluidAmountUnit.FABRIC_TRANSFER) {
            return new FluidConversionResult.Unsupported("Fabric fluid amount unit must be FABRIC_TRANSFER");
        }
        Fluid fluid = variant.getFluid();
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
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
                return fluid.builtInRegistryHolder().is(TagKey.create(Registries.FLUID, tagId));
            }

            @Override
            public boolean matchesExactStack(String encodedStack) {
                return false;
            }
        };
        var bucketItem = fluid.getBucket();
        ItemStack fallback = bucketItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(bucketItem);
        return new FluidConversionResult.Success(new FluidIngredient(variant, input.amount(), fluidId,
            FluidVariantAttributes.getName(variant), fallback, view));
    }

    @Override
    public FluidConversionResult convertLegacyFluid(Object value) {
        if (value instanceof FluidVariant) {
            return new FluidConversionResult.Unsupported("Fabric FluidVariant requires an explicit FABRIC_TRANSFER amount");
        }
        return IPlatformHelper.super.convertLegacyFluid(value);
    }
}
