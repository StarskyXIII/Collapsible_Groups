package com.starskyxiii.collapsible_groups.client.editor;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupDisplayName;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.GroupTheme;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class GroupEditorDefinitionFactoryTest {
	@Test
	void createPreservesExistingIconsAndTranslationKey() {
		JsonObject extra = new JsonObject();
		extra.addProperty("foreign_key", "keep");
		GroupTheme theme = new GroupTheme("#FFAA00", null, null, null, "#66FFFFFF");
		GroupDefinition existing = new GroupDefinition(
			"test_group",
			new GroupDisplayName.Localized("custom.translation.key", "Old Name"),
			true,
			Filters.itemId("minecraft:stone"),
			List.of("minecraft:diamond", "minecraft:emerald"),
			theme,
			4,
			extra
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
		assertEquals(4, saved.priority());
		assertEquals(extra, saved.extra());
	}

	@Test
	void createAppliesEditableAppearanceDraftAndPriority() {
		JsonObject extra = new JsonObject();
		extra.addProperty("foreign_key", "keep");
		GroupDefinition existing = new GroupDefinition(
			"test_group",
			new GroupDisplayName.Localized("custom.translation.key", "Old Name"),
			true,
			Filters.itemId("minecraft:stone"),
			List.of("minecraft:stone"),
			new GroupTheme("#FFAA00", null, null, null, null),
			2,
			extra
		);
		AppearanceDraft appearance = AppearanceDraft.fromIconIds(
			List.of("minecraft:emerald", "minecraft:gold_ingot", "minecraft:redstone"),
			GroupTheme.EMPTY
		)
			.withNameColor("#112233")
			.withCollapsedHeaderBackground("#44112233")
			.withExpandedHeaderBackground("#55223344")
			.withExpandedGroupBackground("#66334455")
			.withExpandedGroupBorder("#77445566");

		GroupDefinition saved = GroupEditorDefinitionFactory.create(
			"test_group",
			"New Name",
			true,
			Filters.itemTag("minecraft:logs"),
			existing,
			appearance,
			9
		);

		assertEquals(List.of("minecraft:emerald", "minecraft:gold_ingot", "minecraft:redstone"), saved.iconIds());
		assertEquals(new GroupTheme("#112233", "#44112233", "#55223344", "#66334455", "#77445566"), saved.theme());
		assertEquals(9, saved.priority());
		assertEquals(extra, saved.extra());
		assertEquals("custom.translation.key", saved.displayName().key());
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

	@Test
	void createWithDisplayNamePreservesUntouchedNameMetadata() {
		GroupDisplayName displayName = new GroupDisplayName.Localized("custom.translation.key", "");
		GroupDefinition existing = new GroupDefinition(
			"test_group",
			displayName,
			true,
			Filters.itemId("minecraft:stone"),
			List.of("minecraft:diamond")
		);
		GroupFilter updatedFilter = Filters.itemTag("minecraft:logs");

		GroupDefinition saved = GroupEditorDefinitionFactory.createWithDisplayName(
			"test_group",
			displayName,
			false,
			updatedFilter,
			existing
		);

		assertSame(displayName, saved.displayName());
		assertEquals(List.of("minecraft:diamond"), saved.iconIds());
		assertFalse(saved.enabled());
		assertEquals(updatedFilter, saved.filter());
		assertEquals(0, saved.priority());
		assertEquals(new JsonObject(), saved.extra());
	}
}
