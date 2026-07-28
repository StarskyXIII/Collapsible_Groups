package com.starskyxiii.collapsible_groups;

import com.starskyxiii.collapsible_groups.config.NeoForgeConfig;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.i18n.GroupLangBootstrap;
import com.starskyxiii.collapsible_groups.client.preview.PreviewTooltipComponent;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProviders;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import com.starskyxiii.collapsible_groups.viewer.JeiSoftDependencyBootstrap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.Function;
import java.util.List;

@Mod(Constants.MOD_ID)
public class CollapsibleGroups {

	public CollapsibleGroups(IEventBus eventBus, ModContainer modContainer) {
		// Register mod configuration (config/collapsiblegroups/collapsiblegroups.toml)
		modContainer.registerConfig(ModConfig.Type.CLIENT, NeoForgeConfig.SPEC,
			"collapsiblegroups/collapsiblegroups.toml");
		// Register NeoForge's built-in configuration screen (Mods -> Config button)
		modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

		eventBus.addListener(this::onClientSetup);
		eventBus.addListener(this::onConfigReload);
		eventBus.addListener(this::registerTooltipComponentFactories);
		eventBus.addListener(this::onRegisterReloadListeners);
		NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
		if (ModList.get().isLoaded("emi") || ModList.get().isLoaded(ViewerLifecycleCoordinator.TMRV_MOD_ID)) {
			NeoForge.EVENT_BUS.addListener(this::onClientLogout);
		}

		// Register the KubeJS remote-data listener on the game event bus only when
		// KubeJS is present. The class is loaded lazily so KubeJS types are never
		// touched when the mod is absent.
		if (ModList.get().isLoaded("kubejs")) {
			NeoForge.EVENT_BUS.register(
				com.starskyxiii.collapsible_groups.compat.kubejs.KubeJSRemoteListener.class
			);
		}

		// These bridges reference third-party JEI plugin classes. Keep their class
		// names out of this entrypoint and resolve them only when JEI wins selection.
		JeiSoftDependencyBootstrap.registerSelected(
			ViewerLifecycleCoordinator.isJeiSelected(),
			modId -> ModList.get().isLoaded(modId),
			List.of(
				new JeiSoftDependencyBootstrap.Registration("mekanism",
					"com.starskyxiii.collapsible_groups.compat.softdep.MekanismIngredientTypeLoader"),
				new JeiSoftDependencyBootstrap.Registration("productivebees",
					"com.starskyxiii.collapsible_groups.compat.softdep.ProductiveBeesIngredientTypeLoader")
			)
		);
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

	private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		com.starskyxiii.collapsible_groups.compat.emi.EmiViewerAdapter.unregisterRuntime();
	}

	private void onConfigReload(ModConfigEvent.Reloading event) {
		if (event.getConfig().getSpec() == NeoForgeConfig.SPEC) {
			reloadGroupsFromCurrentConfig();
			GroupRepository.notifyViewer();
		}
	}

	private void onClientSetup(FMLClientSetupEvent event) {
		if (ModList.get().isLoaded("kubejs")) {
			ViewerLifecycleCoordinator.global().setScriptedGroupBootstrap(
				com.starskyxiii.collapsible_groups.compat.kubejs.KubeJSGroupBridge::applyGroupsNeutral
			);
		}
		reloadGroupsFromCurrentConfig();
	}

	public static void reloadGroupsFromCurrentConfig() {
		GroupLangBootstrap.refresh();
		GroupRepository.load(DefaultGroupProviders.loadAll("NeoForge", 8));
	}

	private void registerTooltipComponentFactories(RegisterClientTooltipComponentFactoriesEvent event) {
		event.register(PreviewTooltipComponent.class, Function.identity());
	}
}
