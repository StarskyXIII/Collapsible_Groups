package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.group.*;
import com.starskyxiii.collapsible_groups.group.filter.*;
import com.starskyxiii.collapsible_groups.client.editor.GroupEditorDefinitionFactory;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GroupConfigVersionedItemDataTest {
	@BeforeAll static void bootstrapMinecraft() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

	@Test void blankGroupUsesV1AndLegacyEditCopyKeepsLegacy() {
		GroupDefinition fresh = GroupEditorDefinitionFactory.create("new_group", "New", true, Filters.itemId("minecraft:stone"), null);
		assertEquals(1, json(fresh).get("schema_version").getAsInt());
		GroupDefinition legacy = GroupConfig.fromJson("{\"id\":\"legacy\",\"name\":\"Legacy\",\"filter\":{\"type\":\"item\",\"id\":\"minecraft:stone\"}}");
		assertEquals(GroupDocumentFormat.LEGACY, legacy.documentFormat());
		var edited = GroupEditorDefinitionFactory.create("copy", "Copy", false, legacy.filter(), legacy);
		assertFalse(json(edited).has("schema_version"));
		assertEquals(GroupDocumentFormat.LEGACY, GroupConfig.fromJson(GroupConfig.toJson(edited)).documentFormat());
		assertEquals(GroupDocumentFormat.V1, GroupEditorDefinitionFactory.create("copy", "Copy", true, fresh.filter(), fresh).documentFormat());
	}

	@Test void invalidDocumentVersionsPreserveEntireDocumentThroughEveryCopyAndBlockSaves() {
		for (String version : List.of("2", "\"1\"", "1.5", "null", "true", "{}")) {
			String source = "{\"schema_version\":" + version + ",\"id\":\"future\",\"future\":{\"opaque\":[1,true]},\"filter\":{\"type\":\"item\",\"id\":\"minecraft:stone\"}}";
			JsonObject raw = JsonParser.parseString(source).getAsJsonObject();
			GroupDefinition group = GroupConfig.fromJson(source);
			assertNotNull(group);
			assertEquals(GroupDocumentFormat.UNSUPPORTED, group.documentFormat());
			assertTrue(group.hasUnavailableFilter());
			var changed = group.withEnabled(true).withName("Changed").withIconIds(List.of("minecraft:dirt"))
				.withPriority(5).withTheme(GroupTheme.EMPTY).withExtra(new JsonObject()).withFilter(Filters.itemId("minecraft:stone"));
			assertEquals(raw, json(changed));
			assertFalse(changed.matchesIgnoringEnabled(new ItemStack(Items.STONE)));
			group.rawDocument().addProperty("future", "mutated");
			assertEquals(raw, group.rawDocument());
			assertFalse(GroupConfig.saveChecked(changed));
			assertThrows(IllegalArgumentException.class, () -> GroupEditorDefinitionFactory.create("copy", "Copy", true, group.filter(), group));
		}
	}

	@Test void nestedNbtAndExactPayloadRoundTripWithoutInternalEnvelope() {
		GroupFilter filter = Filters.any(Filters.nbt("{a:1b}"), Filters.not(Filters.nbtPath("value", "1L")), Filters.exactStack(new ItemStack(Items.STONE, 32)));
		GroupDefinition group = GroupDefinition.of("typed", "Typed", filter);
		assertEquals(GroupDocumentFormat.V1, group.documentFormat());
		String saved = GroupConfig.toJson(group);
		assertFalse(saved.contains("source_minecraft"));
		assertEquals(group, GroupConfig.fromJson(saved));
		var rules = GroupFilterRuleDraft.decode(group.filter());
		assertEquals(group.filter(), rules.copy().toFilter().orElseThrow());
	}

	@Test void foreignComponentTypedValuesRemainOpaqueAndUnchanged() {
		for (String literal : List.of("\"1\"", "1", "true", "{}", "[]", "\"\"", "null", "\"null\"")) {
			String source = "{\"schema_version\":1,\"id\":\"foreign\",\"filter\":{\"type\":\"item\",\"component\":\"minecraft:custom_data\",\"path\":\"a\",\"value\":{\"data_format\":\"minecraft:data_component\",\"data\":" + literal + "}}}";
			GroupDefinition group = GroupConfig.fromJson(source);
			assertInstanceOf(GroupFilter.Unsupported.class, group.filter());
			assertEquals(JsonParser.parseString(source).getAsJsonObject().get("filter"), json(group).get("filter"));
		}
	}

	@Test void publicLegacySaveCannotWriteNewNativeData() {
		GroupDefinition legacy = GroupDefinition.of("legacy", "Legacy", Filters.itemId("minecraft:stone"));
		GroupDefinition changed = legacy.withFilter(Filters.nbt("{a:1b}"));
		assertEquals(GroupDocumentFormat.LEGACY, changed.documentFormat());
		assertFalse(GroupConfig.saveChecked(changed));
		assertThrows(IllegalArgumentException.class, () -> GroupConfig.toJson(changed));
	}

	@Test void publicSaveDoesNotOverwriteUnknownDocumentOrCreateMixedLegacyFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
		String key = com.starskyxiii.collapsible_groups.platform.TestPlatformHelper.CONFIG_DIR_PROPERTY;
		String previous = System.getProperty(key);
		System.setProperty(key, root.toString());
		try {
			java.nio.file.Path directory = root.resolve("collapsiblegroups/groups");
			java.nio.file.Files.createDirectories(directory);
			String raw = "{\"schema_version\":null,\"id\":\"future\",\"opaque\":true}";
			java.nio.file.Path file = directory.resolve("different-filename.json");
			java.nio.file.Files.writeString(file, raw);
			GroupConfig.save(GroupDefinition.of("future", "Replacement", Filters.itemId("minecraft:stone")));
			assertEquals(raw, java.nio.file.Files.readString(file));
			assertFalse(java.nio.file.Files.exists(directory.resolve("future.json")));
			GroupDefinition legacy = GroupDefinition.of("legacy", "Legacy", Filters.itemId("minecraft:stone"));
			assertTrue(GroupConfig.saveChecked(legacy));
			String original = java.nio.file.Files.readString(directory.resolve("legacy.json"));
			GroupConfig.save(legacy.withFilter(Filters.nbt("{}")));
			assertEquals(original, java.nio.file.Files.readString(directory.resolve("legacy.json")));
		} finally {
			if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
		}
	}

	@Test void actualTargetFileIsProtectedRegardlessOfItsInternalId(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
		String key = com.starskyxiii.collapsible_groups.platform.TestPlatformHelper.CONFIG_DIR_PROPERTY;
		String previous = System.getProperty(key);
		System.setProperty(key, root.toString());
		try {
			java.nio.file.Path directory = root.resolve("collapsiblegroups/groups");
			java.nio.file.Files.createDirectories(directory);
			java.nio.file.Path file = directory.resolve("foo.json");
			GroupDefinition replacement = GroupEditorDefinitionFactory.create("foo", "New group", true,
				Filters.itemId("minecraft:stone"), null);
			for (String version : List.of("2", "null")) {
				byte[] original = ("{\r\n  \"schema_version\": " + version
					+ ", \"id\": \"future_group\", \"opaque\": [1, null]\r\n}\r\n")
					.getBytes(java.nio.charset.StandardCharsets.UTF_8);
				java.nio.file.Files.write(file, original);
				assertFalse(GroupConfig.saveChecked(replacement));
				assertArrayEquals(original, java.nio.file.Files.readAllBytes(file));
				GroupConfig.save(replacement);
				assertArrayEquals(original, java.nio.file.Files.readAllBytes(file));
			}
		} finally {
			if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
		}
	}

	private static JsonObject json(GroupDefinition group) { return JsonParser.parseString(GroupConfig.toJson(group)).getAsJsonObject(); }
}
