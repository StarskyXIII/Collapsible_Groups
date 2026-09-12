package com.starskyxiii.collapsible_groups.platform.services;

import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionInput;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionResult;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidIngredient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;

public interface IPlatformHelper {
	default java.util.List<net.minecraft.server.packs.PackResources> groupResourcePacks(
		net.minecraft.server.packs.resources.ResourceManager manager) {
		try (var packs = manager.listPacks()) { return packs.toList(); }
	}

	default FluidConversionResult convertFluid(FluidConversionInput input) {
		return new FluidConversionResult.Unsupported(getPlatformName() + " does not support native fluid conversion");
	}

	default FluidConversionResult convertLegacyFluid(Object value) {
		if (value instanceof FluidIngredient ingredient) return new FluidConversionResult.Success(ingredient);
		return new FluidConversionResult.Unsupported(
			getPlatformName() + " does not support legacy fluid value " +
				(value == null ? "null" : value.getClass().getName()));
	}

	default FluidIngredient requireFluid(Object value) {
		return convertLegacyFluid(value).require();
	}

    /**
     * Returns the root config directory for this platform (e.g. {@code .minecraft/config}).
     * Use this instead of platform-specific APIs like {@code FMLPaths.CONFIGDIR}.
     */
    Path getConfigDir();

    /** Returns the platform name, e.g. {@code "NeoForge"}, {@code "Fabric"}, or {@code "Forge"}. */
    String getPlatformName();

    /** Returns true if the mod with the given ID is loaded, e.g. {@code "jei"} or {@code "chipped"}. */
    boolean isModLoaded(String modId);

    /** Returns true when running in a development environment (enables additional debug checks). */
    boolean isDevelopmentEnvironment();

    /** Returns the environment name: {@code "development"} or {@code "production"}. */
    default String getEnvironmentName() {

        return isDevelopmentEnvironment() ? "development" : "production";
    }

    // -----------------------------------------------------------------------
    // Fluid abstraction (loader-specific FluidStack passed as Object)
    // -----------------------------------------------------------------------

    /**
     * Returns the registry ID of a loader-specific fluid stack, e.g. {@code "minecraft:water"}.
     *
     * @param fluidStack loader-specific fluid stack (e.g. NeoForge {@code FluidStack})
     */
    default String getFluidId(Object fluidStack) {
		return requireFluid(fluidStack).id().toString();
    }

    /**
     * Returns the display name used for a loader-specific fluid ingredient in editor search and tooltips.
     */
    default Component getFluidDisplayName(Object fluidStack) {
		return requireFluid(fluidStack).displayName();
    }

    /**
     * Returns a bucket item fallback for rendering when JEI runtime rendering is unavailable.
     */
    default ItemStack getFluidFallbackBucket(Object fluidStack) {
		return requireFluid(fluidStack).fallbackBucket();
    }

    /**
     * Returns whether a loader-specific fluid stack matches the given registry ID.
     *
     * @param fluidStack loader-specific fluid stack
     * @param id         registry ID to match, e.g. {@code "minecraft:water"}
     */
    default boolean fluidMatchesId(Object fluidStack, String id) {
        return getFluidId(fluidStack).equals(id);
    }

    /**
     * Returns whether a loader-specific fluid stack matches the given tag ID.
     *
     * @param fluidStack loader-specific fluid stack
     * @param tagId      tag ID to match, e.g. {@code "c:water"}
     */
    default boolean fluidMatchesTag(Object fluidStack, String tagId) {
		return requireFluid(fluidStack).view().hasTag(new net.minecraft.resources.ResourceLocation(tagId));
    }

    /**
     * Creates an {@link IngredientView} for a loader-specific fluid stack.
     */
    default IngredientView createFluidView(Object fluidStack) {
		return requireFluid(fluidStack).view();
    }
}
