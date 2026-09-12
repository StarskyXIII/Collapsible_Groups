package com.starskyxiii.collapsible_groups.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraftforge.resource.DelegatingPackResources;
import net.minecraftforge.resource.PathPackResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForgeGroupResourcePacksTest {
    private static final ResourceLocation RESOURCE = new ResourceLocation("test", "collapsible_groups/groups/same.json");
    @TempDir Path directory;

    @Test void excludesOnlyRegisteredOwnPackEvenWhenAnotherPackUsesTheSameJarName() throws Exception {
        var own = pack("own", "collapsible_groups-forge-1.20.1-1.5.0.jar", "bundled");
        var external = pack("external", own.packId(), "external");
        var aggregate = delegated("mod_resources", List.of(external, own));
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(aggregate))) {
            var flattened = ForgePlatformHelper.groupResourcePacks(manager, own);
            assertEquals(List.of(external), flattened);
            assertEquals("external", read(flattened.get(0)));
        }
    }

    @Test void nestedDelegatesPreserveNativePriorityAndEveryDifferentDefinitionAtTheSamePath() throws Exception {
        var low = pack("low", "low.jar", "low");
        var high = pack("high", "high.jar", "high");
        var selected = pack("selected", "file/selected", "selected");
        var aggregate = delegated("mod_resources", List.of(delegated("nested", List.of(high, low))));
        assertEquals("high", read(aggregate));
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(aggregate, selected))) {
            var flattened = ForgePlatformHelper.groupResourcePacks(manager, null);
            assertEquals(List.of(low, high, selected), flattened);
            assertEquals(List.of("low", "high", "selected"), flattened.stream().map(pack -> {
                try { return read(pack); } catch (Exception failure) { throw new AssertionError(failure); }
            }).toList());
            try (var expanded = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, flattened)) {
                assertEquals(3, expanded.getResourceStack(RESOURCE).size());
                try (var input = expanded.getResource(RESOURCE).orElseThrow().open()) {
                    assertEquals("selected", new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
    }

    @Test void standaloneOwnPackIsExcludedAndMissingOwnPackDoesNotDiscardExternalResources() throws Exception {
        var own = pack("own", "development-resources", "bundled");
        var external = pack("external", "other.jar", "external");
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(own, external))) {
            assertEquals(List.of(external), ForgePlatformHelper.groupResourcePacks(manager, own));
            assertEquals(List.of(own, external), ForgePlatformHelper.groupResourcePacks(manager, null));
        }
    }

    @Test void recreatedServerRegistrationStillIdentifiesTheClientPackAcrossReloads() throws Exception {
        var clientPack = pack("own", "renamed-mod.jar", "bundled");
        var serverPack = new PathPackResources(clientPack.packId(), true, clientPack.getSource());
        var external = pack("external", clientPack.packId(), "external");
        assertNotSame(clientPack, serverPack);
        var aggregate = delegated("mod_resources", List.of(external, clientPack));
        for (var registered : List.of(clientPack, serverPack)) {
            try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(aggregate))) {
                assertEquals(List.of(external), ForgePlatformHelper.groupResourcePacks(manager, registered));
            }
        }
    }

    private PathPackResources pack(String folder, String id, String content) throws Exception {
        Path root = directory.resolve(folder);
        Path resource = root.resolve("assets/test/" + RESOURCE.getPath());
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, content, StandardCharsets.UTF_8);
        return new PathPackResources(id, true, root);
    }

    private static DelegatingPackResources delegated(String id, List<PackResources> children) {
        return new DelegatingPackResources(id, false, new PackMetadataSection(Component.literal(id), 15), children);
    }

    private static String read(PackResources pack) throws Exception {
        try (var input = pack.getResource(PackType.CLIENT_RESOURCES, RESOURCE).get()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
