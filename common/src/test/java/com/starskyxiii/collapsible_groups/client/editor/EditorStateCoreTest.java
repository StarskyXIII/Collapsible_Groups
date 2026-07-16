package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.group.GroupTheme;
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
	void pendingRuleNodeIsDeletedOnCancelAndKeptOnCommit() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		GroupFilterRuleDraft.Node root = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.ALL);

		GroupFilterRuleDraft.Node pending = core.insertRuleRelativePending(GroupFilterRuleDraft.NodeKind.TAG);
		assertTrue(core.hasPendingRuleNode());
		assertEquals(1, root.children().size());

		core.cancelPendingRuleNode();
		assertFalse(core.hasPendingRuleNode());
		assertTrue(root.children().isEmpty());
		assertEquals(root, core.selectedRuleNode());

		GroupFilterRuleDraft.Node kept = core.insertRuleRelativePending(GroupFilterRuleDraft.NodeKind.TAG);
		kept.setPrimaryValue("c:missing");
		core.commitPendingRuleNode();
		assertFalse(core.hasPendingRuleNode());
		assertEquals(List.of(kept), root.children());

		core.cancelPendingRuleNode();
		assertEquals(List.of(kept), root.children());
	}

	@Test
	void pendingRootCancelClearsTree() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		core.insertRuleRelativePending(GroupFilterRuleDraft.NodeKind.TAG);
		assertTrue(core.hasRulesRoot());

		core.cancelPendingRuleNode();

		assertFalse(core.hasRulesRoot());
		assertFalse(core.hasPendingRuleNode());
	}

	@Test
	void previewDefinitionUsesAppearanceDraftAndPriority() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		AppearanceDraft appearance = AppearanceDraft.fromIconIds(
			List.of("minecraft:emerald", "minecraft:gold_ingot"),
			new GroupTheme("#112233", "#44112233", "#55223344", "#66334455", "#77445566")
		);

		GroupDefinition preview = core.buildPreviewDefinition(
			"preview_group",
			"Preview Group",
			true,
			appearance,
			8
		);

		assertEquals(List.of("minecraft:emerald", "minecraft:gold_ingot"), preview.iconIds());
		assertEquals(new GroupTheme("#112233", "#44112233", "#55223344", "#66334455", "#77445566"), preview.theme());
		assertEquals(8, preview.priority());
	}

	@Test
	void ruleCoverageSetsAreQueriedByKeyAndRebuiltOnEachUpdate() {
		// the right-panel rebuild converges its single resolve pass into these
		// id sets; the source grid queries them by key. A fresh update fully replaces
		// the previous sets (defensive-copied), so a cell that leaves the group's
		// matches stops reporting as rule-covered.
		EditorStateCore core = new EditorStateCore(null, () -> {});

		// No coverage before the first update.
		assertFalse(core.isItemRuleCovered("minecraft:stone"));

		core.setCoveredSets(
			new java.util.HashSet<>(List.of("minecraft:stone", "minecraft:diamond")),
			new java.util.HashSet<>(List.of("minecraft:water")),
			new java.util.HashSet<>(List.of("mekanism:gas|mekanism:hydrogen")));

		assertTrue(core.isItemRuleCovered("minecraft:stone"));
		assertTrue(core.isItemRuleCovered("minecraft:diamond"));
		assertFalse(core.isItemRuleCovered("minecraft:gold_ingot"));
		assertTrue(core.isFluidRuleCovered("minecraft:water"));
		assertFalse(core.isFluidRuleCovered("minecraft:lava"));
		assertTrue(core.isGenericRuleCovered("mekanism:gas|mekanism:hydrogen"));
		assertFalse(core.isGenericRuleCovered("mekanism:gas|mekanism:oxygen"));

		// Null keys never match; null sets clear coverage.
		assertFalse(core.isItemRuleCovered(null));

		// A later update replaces (not merges) the previous sets.
		core.setCoveredSets(new java.util.HashSet<>(List.of("minecraft:gold_ingot")), null, null);
		assertTrue(core.isItemRuleCovered("minecraft:gold_ingot"));
		assertFalse(core.isItemRuleCovered("minecraft:stone"));
		assertFalse(core.isFluidRuleCovered("minecraft:water"));
		assertFalse(core.isGenericRuleCovered("mekanism:gas|mekanism:hydrogen"));
	}

	@Test
	void unresolvedRuleCountUsesInjectedLookupAndSkipsSyntaxErrors() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		GroupFilterRuleDraft.Node root = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.ALL);
		core.selectRuleNode(root);
		core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.TAG).setPrimaryValue("c:exists");
		core.selectRuleNode(root);
		core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.TAG).setPrimaryValue("c:missing");
		core.selectRuleNode(root);
		core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.TAG).setPrimaryValue("NOT A LOCATION");

		assertEquals(1, core.unresolvedRuleCount((registry, tagId) -> tagId.getPath().equals("exists")));
	}

	@Test
	void hybridDraftPreservesAdvancedSubtreeThroughContentsSync() {
		// any(not(itemTag)) normalizes to a lone not(itemTag) — a preserved advanced
		// subtree. It is editable (contents grid stays live) but not flat-index safe.
		GroupFilterEditorDraft.DecodeResult decoded = GroupFilterEditorDraft.decode(
			Filters.any(Filters.not(Filters.itemTag("c:ingots"))));
		assertTrue(decoded.structurallyEditable());
		assertFalse(decoded.flatIndexSafe());
		assertEquals(List.of(new GroupFilter.Not(new GroupFilter.Tag("item", "c:ingots"))),
			decoded.preservedSubtrees());

		GroupDefinition nested = new GroupDefinition("nested", "Nested", true,
			Filters.any(Filters.not(Filters.itemTag("c:ingots"))));
		EditorStateCore core = new EditorStateCore(nested, () -> {});
		core.setContentsEditability(true, false);

		// A hybrid contents draft carries the preserved Not plus a new explicit selection;
		// syncing rebuilds the rules as Any(preserved…, flat…) with the Not intact.
		GroupFilterEditorDraft draft = GroupFilterEditorDraft.empty();
		draft.preservedSubtrees().addAll(decoded.preservedSubtrees());
		draft.explicitItemSelectors().add("stack:{\"id\":\"minecraft:stone\"}");
		core.syncRulesFromContentsDraft(draft);

		GroupFilter.Any result = assertInstanceOf(GroupFilter.Any.class, core.buildCurrentFilter().orElseThrow());
		assertEquals(List.of(
			new GroupFilter.Not(new GroupFilter.Tag("item", "c:ingots")),
			new GroupFilter.ExactStack("{\"id\":\"minecraft:stone\"}")
		), result.children());
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
