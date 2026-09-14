package com.starskyxiii.collapsible_groups.config;

import net.fabricmc.loader.api.FabricLoader;

public final class FabricConfig extends AcceptedConfigProvider {
    private final JsonSettingsStorage storage = new JsonSettingsStorage(FabricLoader.getInstance()
        .getConfigDir().resolve("collapsiblegroups/collapsiblegroups.json"));

    @Override public SettingsSnapshot read() throws Exception { return storage.read(); }
    @Override public void write(SettingsSnapshot settings) throws Exception { storage.write(settings); }
}
