package com.starskyxiii.collapsible_groups;

import com.starskyxiii.collapsible_groups.compat.jei.preview.PreviewTooltipComponent;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.i18n.GroupLangBootstrap;
import com.starskyxiii.collapsible_groups.config.FabricConfig;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProviders;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
public class CollapsibleGroupsFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Constants.LOG.info("Initializing {} on Fabric", Constants.MOD_NAME);
        CommonClass.init();
        FabricConfig.load();
        reloadGroupsFromCurrentConfig();

        // Reload overlay lang on F3+T resource reload
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override
                public ResourceLocation getFabricId() {
                    return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "overlay_lang");
                }

                @Override
                public void onResourceManagerReload(ResourceManager resourceManager) {
                    GroupLangBootstrap.refresh();
                }
            }
        );

        // Register client commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            com.starskyxiii.collapsible_groups.command.CgClientCommand.register(dispatcher));

        // Register PreviewTooltipComponent so Minecraft renders the ingredient preview grid.
        TooltipComponentCallback.EVENT.register(data ->
            data instanceof PreviewTooltipComponent p ? p : null);
    }

    public static void reloadGroupsFromCurrentConfig() {
        GroupLangBootstrap.refresh();
        GroupRegistry.load(DefaultGroupProviders.loadAll("Fabric", 5));
        GroupRegistry.notifyJei();
    }
}
