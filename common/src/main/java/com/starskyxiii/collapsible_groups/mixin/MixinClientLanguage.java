package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.i18n.GroupLanguageResources;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

@Mixin(ClientLanguage.class)
public abstract class MixinClientLanguage {
    @Redirect(method = "loadFrom", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/packs/resources/ResourceManager;getResourceStack(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;"))
    private static List<Resource> collapsibleGroups$languageStack(ResourceManager manager, ResourceLocation location) {
        return GroupLanguageResources.runtimeStack(manager, location);
    }
}
