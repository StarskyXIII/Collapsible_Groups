package com.starskyxiii.collapsible_groups.mixin;

import net.fabricmc.fabric.impl.resource.loader.GroupResourcePack;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = GroupResourcePack.class, remap = false)
public interface MixinGroupResourcePackAccessor {
    @Accessor("packs")
    List<? extends PackResources> cg$getPacks();
}
