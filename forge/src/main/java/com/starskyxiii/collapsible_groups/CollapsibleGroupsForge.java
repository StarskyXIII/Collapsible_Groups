package com.starskyxiii.collapsible_groups;

import com.starskyxiii.collapsible_groups.compat.jei.preview.PreviewTooltipComponent;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.i18n.GroupLangBootstrap;
import com.starskyxiii.collapsible_groups.config.ForgeConfig;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProviders;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.util.function.Function;

@Mod(Constants.MOD_ID)
public class CollapsibleGroupsForge {

    public CollapsibleGroupsForge() {
        Constants.LOG.info("Initializing {} on Forge", Constants.MOD_NAME);
        CommonClass.init();
        // Register config file: config/collapsiblegroups/collapsiblegroups.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ForgeConfig.SPEC,
            "collapsiblegroups/collapsiblegroups.toml");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerTooltipFactories);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onRegisterReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
    }

    private void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
            (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                resourceManager -> GroupLangBootstrap.refresh()
        );
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        com.starskyxiii.collapsible_groups.command.CgClientCommand.register(event.getDispatcher());
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        reloadGroupsFromCurrentConfig();
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ForgeConfig.SPEC) {
            reloadGroupsFromCurrentConfig();
            GroupRegistry.notifyJei();
        }
    }

    private void registerTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(PreviewTooltipComponent.class, Function.identity());
    }

    public static void reloadGroupsFromCurrentConfig() {
        GroupLangBootstrap.refresh();
        GroupRegistry.load(DefaultGroupProviders.loadAll("Forge", 3));
    }
}
