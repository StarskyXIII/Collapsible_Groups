package com.starskyxiii.collapsible_groups.group;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupCatalogCopyTest {
	@AfterEach
	void resetRegistryState() {
		GroupRepositoryTestAccess.replace(List.of());
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

        GroupDefinition copied = GroupCatalog.createCustomCopy(
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

        GroupDefinition copied = GroupCatalog.createCustomCopy(
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

		GroupRepositoryTestAccess.replace(List.of(user));
		assertEquals(Optional.empty(), GroupRepository.createCustomCopyDraft(user.id(), "Copy"));
		assertEquals(Optional.empty(), GroupCatalog.createCustomCopy(null, "Copy", List.of()));
	}

	@Test
	void generatesStableUniqueIdWhenCopyIdCollides() {
		GroupDefinition source = new GroupDefinition(
			"__default_stone_family",
			"Stone Family",
			true,
			Filters.itemId("minecraft:stone")
		);

        GroupDefinition copied = GroupCatalog.createCustomCopy(
			source,
			"Stone Family Copy",
			List.of(source.id(), "stone_family_copy", "stone_family_copy_2")
		).orElseThrow();

		assertEquals("stone_family_copy_3", copied.id());
	}

	@Test
	void createsCopyDraftWithoutSavingItToRegistry() {
		GroupDefinition source = new GroupDefinition(
			"__default_stone_family",
			"Stone Family",
			true,
			Filters.itemId("minecraft:stone")
		);
		GroupRepositoryTestAccess.replace(List.of(source));

		GroupDefinition draft = GroupRepository.createCustomCopyDraft(source.id(), "Stone Family Copy").orElseThrow();

		assertEquals("stone_family_copy", draft.id());
		assertEquals("Stone Family Copy", draft.displayName().fallback());
		assertTrue(GroupRepository.findById(source.id()).isPresent());
		assertTrue(GroupRepository.findById(draft.id()).isEmpty());
	}

	@Test
	void copyDraftUsesUniqueIdWithoutSavingCollisionCandidate() {
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
		GroupRepositoryTestAccess.replace(List.of(source, existingCopy));

		GroupDefinition draft = GroupRepository.createCustomCopyDraft(source.id(), "Stone Family Copy").orElseThrow();

		assertEquals("stone_family_copy_2", draft.id());
		assertTrue(GroupRepository.findById(draft.id()).isEmpty());
	}

}
