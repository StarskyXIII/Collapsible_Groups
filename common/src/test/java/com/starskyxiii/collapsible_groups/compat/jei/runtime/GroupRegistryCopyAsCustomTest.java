package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupTheme;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupRegistryCopyAsCustomTest {
	@AfterEach
	void resetRegistryState() throws Exception {
		replaceRegistrySnapshot(List.of());
		KubeJsGroupStore.clearAll();
	}

	@Test
	void copiesBuiltinToUserEditableGroupWithoutReservedPrefix() {
		JsonObject extra = new JsonObject();
		extra.addProperty("foreign_key", "keep");
		GroupTheme theme = new GroupTheme("#FFAA00", null, "#FF112233", null, "#66112233");
		GroupDefinition source = new GroupDefinition(
			"__default_stone_family",
			"Stone Family",
			false,
			Filters.itemTag("minecraft:stone_tool_materials"),
			List.of("minecraft:stone", "minecraft:cobblestone"),
			theme,
			6,
			extra
		);

		GroupDefinition copied = GroupRegistry.createCustomCopy(
			source,
			"Stone Family Copy",
			List.of(source.id())
		).orElseThrow();

		assertEquals("stone_family_copy", copied.id());
		assertFalse(copied.id().startsWith("__default_"));
		assertFalse(copied.id().startsWith("__kjs_"));
		assertEquals("Stone Family Copy", copied.displayName().fallback());
		assertEquals(GroupTranslationHelper.keyForGroupId(copied.id()), copied.displayName().key());
		assertEquals(source.enabled(), copied.enabled());
		assertEquals(source.filter(), copied.filter());
		assertEquals(source.iconIds(), copied.iconIds());
		assertEquals(source.theme(), copied.theme());
		assertEquals(source.priority(), copied.priority());
		assertEquals(source.extra(), copied.extra());
	}

	@Test
	void copiesKubeJsToUserEditableGroupWithoutKubeJsPrefix() {
		GroupDefinition source = new GroupDefinition(
			"__kjs_scripted_group",
			"Scripted Group",
			true,
			Filters.itemId("minecraft:diamond")
		);

		GroupDefinition copied = GroupRegistry.createCustomCopy(
			source,
			"Scripted Group Copy",
			List.of(source.id())
		).orElseThrow();

		assertEquals("scripted_group_copy", copied.id());
		assertFalse(copied.id().startsWith("__kjs_"));
		assertEquals("Scripted Group Copy", copied.displayName().fallback());
		assertEquals(GroupTranslationHelper.keyForGroupId(copied.id()), copied.displayName().key());
	}

	@Test
	void rejectsUserGroupsAndMissingSources() {
		GroupDefinition user = new GroupDefinition(
			"custom_group",
			"Custom Group",
			true,
			Filters.itemId("minecraft:stone")
		);

		assertEquals(Optional.empty(), GroupRegistry.createCustomCopy(user, "Copy", List.of(user.id())));
		assertEquals(Optional.empty(), GroupRegistry.createCustomCopy(null, "Copy", List.of()));
	}

	@Test
	void generatesStableUniqueIdWhenCopyIdCollides() {
		GroupDefinition source = new GroupDefinition(
			"__default_stone_family",
			"Stone Family",
			true,
			Filters.itemId("minecraft:stone")
		);

		GroupDefinition copied = GroupRegistry.createCustomCopy(
			source,
			"Stone Family Copy",
			List.of(source.id(), "stone_family_copy", "stone_family_copy_2")
		).orElseThrow();

		assertEquals("stone_family_copy_3", copied.id());
	}

	@Test
	void createsCopyDraftWithoutSavingItToRegistry() throws Exception {
		GroupDefinition source = new GroupDefinition(
			"__default_stone_family",
			"Stone Family",
			true,
			Filters.itemId("minecraft:stone")
		);
		replaceRegistrySnapshot(List.of(source));

		GroupDefinition draft = GroupRegistry.createCustomCopyDraft(source.id(), "Stone Family Copy").orElseThrow();

		assertEquals("stone_family_copy", draft.id());
		assertEquals("Stone Family Copy", draft.displayName().fallback());
		assertTrue(GroupRegistry.findById(source.id()).isPresent());
		assertTrue(GroupRegistry.findById(draft.id()).isEmpty());
	}

	@Test
	void copyDraftUsesUniqueIdWithoutSavingCollisionCandidate() throws Exception {
		GroupDefinition source = new GroupDefinition(
			"__default_stone_family",
			"Stone Family",
			true,
			Filters.itemId("minecraft:stone")
		);
		GroupDefinition existingCopy = new GroupDefinition(
			"stone_family_copy",
			"Existing Copy",
			true,
			Filters.itemId("minecraft:cobblestone")
		);
		replaceRegistrySnapshot(List.of(source, existingCopy));

		GroupDefinition draft = GroupRegistry.createCustomCopyDraft(source.id(), "Stone Family Copy").orElseThrow();

		assertEquals("stone_family_copy_2", draft.id());
		assertTrue(GroupRegistry.findById(draft.id()).isEmpty());
	}

	private static void replaceRegistrySnapshot(List<GroupDefinition> groups) throws Exception {
		Field groupsField = GroupRegistry.class.getDeclaredField("groups");
		groupsField.setAccessible(true);
		groupsField.set(null, List.copyOf(groups));

		Field orderedGroupsField = GroupRegistry.class.getDeclaredField("orderedGroups");
		orderedGroupsField.setAccessible(true);
		orderedGroupsField.set(null, GroupRegistry.orderByPriority(groups));

		Map<String, GroupDefinition> byId = new LinkedHashMap<>();
		for (GroupDefinition group : groups) {
			byId.put(group.id(), group);
		}
		Field groupsByIdField = GroupRegistry.class.getDeclaredField("groupsById");
		groupsByIdField.setAccessible(true);
		groupsByIdField.set(null, Map.copyOf(byId));
	}
}
