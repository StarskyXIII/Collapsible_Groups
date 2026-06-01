package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupDisplayName;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupTheme;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class GroupEditorDefinitionFactoryTest {
	@Test
	void createPreservesExistingIconsAndTranslationKey() {
		GroupTheme theme = new GroupTheme("#FFAA00", null, null, null, "#66FFFFFF");
		GroupDefinition existing = new GroupDefinition(
			"test_group",
			new GroupDisplayName.Localized("custom.translation.key", "Old Name"),
			true,
			Filters.itemId("minecraft:stone"),
			List.of("minecraft:diamond", "minecraft:emerald"),
			theme
		);
		GroupFilter updatedFilter = Filters.itemTag("minecraft:planks");

		GroupDefinition saved = GroupEditorDefinitionFactory.create(
			"test_group",
			"New Name",
			false,
			updatedFilter,
			existing
		);

		assertEquals(List.of("minecraft:diamond", "minecraft:emerald"), saved.iconIds());
		assertEquals("custom.translation.key", saved.displayName().key());
		assertEquals("New Name", saved.displayName().fallback());
		assertFalse(saved.enabled());
		assertEquals(updatedFilter, saved.filter());
		assertEquals(theme, saved.theme());
	}

	@Test
	void createUsesGeneratedTranslationKeyForNewGroup() {
		GroupDefinition saved = GroupEditorDefinitionFactory.create(
			"new_group",
			"New Group",
			true,
			Filters.itemId("minecraft:stone"),
			null
		);

		assertEquals(List.of(), saved.iconIds());
		assertSame(GroupTheme.EMPTY, saved.theme());
		assertEquals(GroupTranslationHelper.keyForGroupId("new_group"), saved.displayName().key());
		assertEquals("New Group", saved.displayName().fallback());
	}

	@Test
	void createKeepsIconsButRegeneratesTranslationKeyWhenIdChanges() {
		GroupDefinition existing = new GroupDefinition(
			"old_group",
			new GroupDisplayName.Localized("custom.translation.key", "Old Name"),
			true,
			Filters.itemId("minecraft:stone"),
			List.of("minecraft:diamond")
		);

		GroupDefinition copied = GroupEditorDefinitionFactory.create(
			"copied_group",
			"Copied Group",
			true,
			Filters.itemId("minecraft:stone"),
			existing
		);

		assertEquals(List.of("minecraft:diamond"), copied.iconIds());
		assertEquals(GroupTranslationHelper.keyForGroupId("copied_group"), copied.displayName().key());
		assertEquals("Copied Group", copied.displayName().fallback());
	}
}
