package com.starskyxiii.collapsible_groups;

import com.starskyxiii.collapsible_groups.config.NeoForgeConfig;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.i18n.GroupLangBootstrap;
import com.starskyxiii.collapsible_groups.compat.jei.preview.PreviewTooltipComponent;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProviders;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.Function;

@Mod(Constants.MOD_ID)
public class CollapsibleGroups {

	public CollapsibleGroups(IEventBus eventBus, ModContainer modContainer) {
		// Register mod configuration (config/collapsiblegroups/collapsiblegroups.toml)
		modContainer.registerConfig(ModConfig.Type.CLIENT, NeoForgeConfig.SPEC,
			"collapsiblegroups/collapsiblegroups.toml");
		// Register NeoForge's built-in configuration screen (Mods ??Config button)
		modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

		eventBus.addListener(this::onClientSetup);
		eventBus.addListener(this::onConfigReload);
		eventBus.addListener(this::registerTooltipComponentFactories);
		eventBus.addListener(this::onRegisterReloadListeners);
		NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);

		// Register the KubeJS remote-data listener on the game event bus only when
		// KubeJS is present. The class is loaded lazily so KubeJS types are never
		// touched when the mod is absent.
		if (ModList.get().isLoaded("kubejs")) {
			NeoForge.EVENT_BUS.register(
				com.starskyxiii.collapsible_groups.compat.kubejs.KubeJSRemoteListener.class
			);
		}

		// Register curated ingredient types for known mods via reflection so we
		// have no compile-time dependency on them.
		if (ModList.get().isLoaded("mekanism")) {
			com.starskyxiii.collapsible_groups.compat.softdep.MekanismIngredientTypeLoader.register();
		}
		if (ModList.get().isLoaded("productivebees")) {
			com.starskyxiii.collapsible_groups.compat.softdep.ProductiveBeesIngredientTypeLoader.register();
		}
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

	private void onConfigReload(ModConfigEvent.Reloading event) {
		if (event.getConfig().getSpec() == NeoForgeConfig.SPEC) {
			reloadGroupsFromCurrentConfig();
			GroupRegistry.notifyJei();
		}
	}

	private void onClientSetup(FMLClientSetupEvent event) {
		reloadGroupsFromCurrentConfig();
	}

	public static void reloadGroupsFromCurrentConfig() {
		GroupLangBootstrap.refresh();
		GroupRegistry.load(DefaultGroupProviders.loadAll("NeoForge", 8));
	}

	private void registerTooltipComponentFactories(RegisterClientTooltipComponentFactoriesEvent event) {
		event.register(PreviewTooltipComponent.class, Function.identity());
	}
}
