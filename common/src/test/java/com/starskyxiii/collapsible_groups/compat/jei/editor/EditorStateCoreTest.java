package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterEditorDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EditorStateCoreTest {
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
