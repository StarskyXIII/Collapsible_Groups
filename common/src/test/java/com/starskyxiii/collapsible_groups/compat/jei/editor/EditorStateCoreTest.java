package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterEditorDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorStateCoreTest {
	@Test
	void launchStateTracksNewEditAndCopySourceIdentity() {
		GroupDefinition existing = new GroupDefinition("existing_group", "Existing Group", true,
			Filters.itemId("minecraft:stone"));
		EditorStateCore newCore = new EditorStateCore(null, () -> {});
		EditorStateCore editCore = new EditorStateCore(existing, false, () -> {});
		EditorStateCore copyCore = new EditorStateCore(existing, true, "source_group", () -> {});
		EditorStateCore legacyCopyCore = new EditorStateCore(existing, true, " ", () -> {});

		assertFalse(newCore.saveAsNew());
		assertNull(newCore.sourceGroupId());
		assertFalse(editCore.saveAsNew());
		assertNull(editCore.sourceGroupId());
		assertTrue(copyCore.saveAsNew());
		assertEquals("source_group", copyCore.sourceGroupId());
		assertTrue(legacyCopyCore.saveAsNew());
		assertNull(legacyCopyCore.sourceGroupId());
	}

	@Test
	void syncingContentsDraftReplacesSavedRulesFilterWithAllManualSelections() {
		GroupFilterEditorDraft draft = GroupFilterEditorDraft.empty();
		draft.explicitItemSelectors().add("stack:{\"id\":\"minecraft:stone\"}");
		draft.explicitItemSelectors().add("stack:{\"id\":\"minecraft:oak_boat\"}");
		EditorStateCore core = new EditorStateCore(null, () -> {});
		core.setContentsQuickEditAvailable(true);

		core.syncRulesFromContentsDraft(draft);

		GroupFilter.Any filter = assertInstanceOf(GroupFilter.Any.class, core.buildCurrentFilter().orElseThrow());
		assertEquals(List.of(
			new GroupFilter.ExactStack("{\"id\":\"minecraft:stone\"}"),
			new GroupFilter.ExactStack("{\"id\":\"minecraft:oak_boat\"}")
		), filter.children());
	}
}
