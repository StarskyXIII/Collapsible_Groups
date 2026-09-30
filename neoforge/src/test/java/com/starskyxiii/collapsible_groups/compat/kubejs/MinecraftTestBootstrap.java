package com.starskyxiii.collapsible_groups.compat.kubejs;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.ItemTags;
import net.neoforged.neoforge.common.CommonHooks;

final class MinecraftTestBootstrap {
    private static boolean initialized;
    static synchronized void initialize() {
        if (initialized) return;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        CommonHooks.markComponentClassAsValid(HolderSet.emptyNamed(BuiltInRegistries.ITEM, ItemTags.PLANKS).getClass());
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup()).forEach(pending -> pending.apply());
        initialized = true;
    }
}
