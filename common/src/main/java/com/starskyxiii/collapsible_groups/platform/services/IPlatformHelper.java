package com.starskyxiii.collapsible_groups.platform.services;

import com.starskyxiii.collapsible_groups.core.IngredientView;
import mezz.jei.api.ingredients.IIngredientType;

import java.nio.file.Path;

public interface IPlatformHelper {

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
        throw new UnsupportedOperationException("getFluidId not implemented for " + getPlatformName());
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
        throw new UnsupportedOperationException("fluidMatchesTag not implemented for " + getPlatformName());
    }

    /**
     * Creates an {@link IngredientView} for a loader-specific fluid stack.
     */
    default IngredientView createFluidView(Object fluidStack) {
        throw new UnsupportedOperationException("createFluidView not implemented for " + getPlatformName());
    }

    /**
     * Returns the platform-specific JEI fluid ingredient type, or {@code null} if unsupported.
     * <p>NeoForge returns {@code NeoForgeTypes.FLUID_STACK}; Fabric/Forge return {@code null}.
     */
    default IIngredientType<?> getJeiFluidType() {
        return null;
    }
}
