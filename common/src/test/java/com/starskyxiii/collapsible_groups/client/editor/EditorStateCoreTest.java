package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.group.GroupTheme;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchDocument;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorStateCoreTest {
	@Test void compiledSnapshotDetectsSameSizeReplacementAndReordering() {
		EditorStateCore core = new EditorStateCore(new GroupDefinition("test", "Test", true,
			Filters.any(Filters.itemId("minecraft:stone"), Filters.itemId("minecraft:dirt"))), () -> {});
		var original = core.buildCurrentFilter();
		var children = core.selectedRuleNode().children();
		java.util.Collections.swap(children, 0, 1);
		var reordered = core.buildCurrentFilter();
		org.junit.jupiter.api.Assertions.assertNotSame(original, reordered);
		assertEquals(Filters.any(Filters.itemId("minecraft:dirt"), Filters.itemId("minecraft:stone")), reordered.orElseThrow());
		var replacement = GroupFilterRuleDraft.decode(Filters.itemId("minecraft:diamond")).root();
		children.set(0, replacement);
		assertEquals(Filters.any(Filters.itemId("minecraft:diamond"), Filters.itemId("minecraft:stone")),
			core.buildCurrentFilter().orElseThrow());
		assertSame(core.buildCurrentFilter(), core.buildCurrentFilter());
	}

	@Test void rawFieldChangesInvalidateEvenWithoutNotification() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		var node = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.ID);
		node.setPrimaryValue("minecraft:stone");
		var id = core.buildCurrentFilter();
		node.setIngredientType("fluid");
		assertEquals(Filters.id("fluid", "minecraft:stone"), core.buildCurrentFilter().orElseThrow());
		org.junit.jupiter.api.Assertions.assertNotSame(id, core.buildCurrentFilter());
		node.setKind(GroupFilterRuleDraft.NodeKind.COMPONENT_PATH);
		node.setPrimaryValue("minecraft:custom_data");
		node.setSecondaryValue("marker");
		node.setTertiaryValue("1");
		var first = core.buildCurrentFilter();
		node.setSecondaryValue("other");
		var second = core.buildCurrentFilter();
		org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
		node.setTertiaryValue("2");
		org.junit.jupiter.api.Assertions.assertNotEquals(second, core.buildCurrentFilter());
	}

	@Test void cachedFilterFollowsContentsWrapDeleteAndCancelledTransaction() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		core.setContentsQuickEditAvailable(true);
		var contents = GroupFilterEditorDraft.empty();
		contents.explicitItemSelectors().add("minecraft:stone");
		core.syncRulesFromContentsDraft(contents);
		var original = core.buildCurrentFilter().orElseThrow();
		assertEquals(Filters.itemId("minecraft:stone"), original);
		var node = core.selectedRuleNode();
		assertTrue(core.beginRuleEdit(node));
		node.setPrimaryValue("minecraft:dirt");
		assertEquals(Filters.itemId("minecraft:dirt"), core.buildCurrentFilter().orElseThrow());
		core.cancelRuleEdit();
		assertEquals(original, core.buildCurrentFilter().orElseThrow());
		core.wrapSelectedRule(GroupFilterRuleDraft.NodeKind.NOT);
		assertEquals(Filters.not(original), core.buildCurrentFilter().orElseThrow());
		core.deleteSelectedRule();
		assertTrue(core.buildCurrentFilter().isEmpty());
		assertFalse(core.canSave("Test"));
		assertSame(core.buildCurrentFilter(), core.buildCurrentFilter());
	}

	@Test void exactSnapshotKeepsDocumentDataFormatAfterUnannouncedEdit() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		var node = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.EXACT_STACK);
		node.setPrimaryValue("{\"id\":\"minecraft:stone\",\"count\":1}");
		var first = assertInstanceOf(GroupFilter.ExactStack.class, core.buildCurrentFilter().orElseThrow());
		assertEquals("minecraft:item_components", first.payload().dataFormat());
		node.setPrimaryValue("{\"id\":\"minecraft:dirt\",\"count\":1}");
		var second = assertInstanceOf(GroupFilter.ExactStack.class, core.buildCurrentFilter().orElseThrow());
		assertEquals(first.payload().dataFormat(), second.payload().dataFormat());
		assertEquals("minecraft:dirt", second.payload().data().getAsJsonObject().get("id").getAsString());
		assertSame(second, core.buildCurrentFilter().orElseThrow());
	}

	@Test
	void deletingNamespaceKeepsPreviewEmptyAcrossPendingPickerKinds() {
		for (String type : List.of("item", "fluid", "emi:mekanism_chemical")) {
			EditorStateCore core = new EditorStateCore(null, () -> {});
			var view = new com.starskyxiii.collapsible_groups.ingredient.IngredientView() {
				public String ingredientType() { return type; }
				public net.minecraft.resources.ResourceLocation resourceLocation() { return net.minecraft.resources.ResourceLocation.parse("mekanism:oxygen"); }
				public boolean hasTag(net.minecraft.resources.ResourceLocation tag) { return false; }
				public boolean matchesExactStack(String value) { return false; }
			};
			GroupFilter empty = core.buildPreviewDefinition(null, "", true).filter();
			var node = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.NAMESPACE);
			node.setIngredientType(type);
			node.setPrimaryValue("mekanism");
			assertEquals(Filters.namespace(type, "mekanism"), core.buildPreviewDefinition(null, "", true).filter());
			assertTrue(com.starskyxiii.collapsible_groups.group.filter.CompiledFilter.compile(core.buildPreviewDefinition(null, "", true).filter()).matches(view));
			core.deleteSelectedRule();
			assertEquals(empty, core.buildPreviewDefinition(null, "", true).filter());
			for (var kind : List.of(GroupFilterRuleDraft.NodeKind.NAMESPACE, GroupFilterRuleDraft.NodeKind.ID, GroupFilterRuleDraft.NodeKind.TAG)) {
				var pending = core.beginInsertRule(kind);
				pending.setIngredientType(type);
				assertFalse(com.starskyxiii.collapsible_groups.group.filter.CompiledFilter.compile(core.buildPreviewDefinition(null, "", true).filter()).matches(view));
				core.cancelRuleEdit();
				assertEquals(empty, core.buildPreviewDefinition(null, "", true).filter());
			}
		}
	}

	@Test
	void deletingGenericIdDoesNotRestoreItWhileAnotherIdPickerIsPending() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		GroupFilter empty = core.buildPreviewDefinition(null, "", true).filter();
		var id = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.ID);
		id.setIngredientType("emi:mekanism_chemical");
		id.setPrimaryValue("mekanism:oxygen");
		assertEquals(Filters.id("emi:mekanism_chemical", "mekanism:oxygen"),
			core.buildPreviewDefinition(null, "", true).filter());
		core.deleteSelectedRule();
		assertEquals(empty, core.buildPreviewDefinition(null, "", true).filter());
		var pending = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.ID);
		pending.setIngredientType("emi:mekanism_chemical");
		assertEquals(empty, core.buildPreviewDefinition(null, "", true).filter());
		core.cancelRuleEdit();
		assertEquals(empty, core.buildPreviewDefinition(null, "", true).filter());
	}

	@Test
	void deletingLastTagClearsFallbackBeforeOpeningAnotherPicker() {
		for (String nextType : List.of("item", "fluid", "emi:mekanism_chemical")) {
			EditorStateCore core = new EditorStateCore(null, () -> {});
			GroupFilter empty = core.buildPreviewDefinition(null, "", true).filter();
			var tag = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.TAG);
			tag.setIngredientType("emi:mekanism_chemical");
			tag.setPrimaryValue("mekanism:clean");
			core.commitRuleEdit();
			assertEquals(Filters.tag("emi:mekanism_chemical", "mekanism:clean"),
				core.buildPreviewDefinition(null, "", true).filter());
			core.deleteSelectedRule();
			assertEquals(empty, core.buildPreviewDefinition(null, "", true).filter());
			var pending = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.TAG);
			pending.setIngredientType(nextType);
			assertEquals(empty, core.buildPreviewDefinition(null, "", true).filter(), nextType);
			core.cancelRuleEdit();
			assertEquals(empty, core.buildPreviewDefinition(null, "", true).filter());
		}
	}

	@Test
	void incompleteEditStillKeepsCurrentValidPreview() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		var tag = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.TAG);
		tag.setIngredientType("emi:mekanism_chemical");
		tag.setPrimaryValue("mekanism:clean");
		GroupFilter valid = core.buildPreviewDefinition(null, "", true).filter();
		tag.setPrimaryValue("");
		assertEquals(valid, core.buildPreviewDefinition(null, "", true).filter());
	}

	@Test
	void deletingLastCompoundChildClearsFallbackBeforeAnotherPendingRule() {
		for (var kind : List.of(GroupFilterRuleDraft.NodeKind.ALL, GroupFilterRuleDraft.NodeKind.ANY,
			GroupFilterRuleDraft.NodeKind.NOT)) {
			EditorStateCore core = new EditorStateCore(null, () -> {});
			GroupFilter empty = core.buildPreviewDefinition(null, "", true).filter();
			var tag = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.TAG);
			tag.setIngredientType("emi:mekanism_chemical");
			tag.setPrimaryValue("mekanism:clean");
			var parent = core.wrapSelectedRule(kind);
			core.buildPreviewDefinition(null, "", true);
			core.selectRuleNode(tag);
			core.deleteSelectedRule();
			assertTrue(parent.children().isEmpty());
			assertEquals(empty, core.buildPreviewDefinition(null, "", true).filter());
			core.selectRuleNode(parent);
			var pending = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.TAG);
			pending.setIngredientType("fluid");
			assertEquals(empty, core.buildPreviewDefinition(null, "", true).filter(), kind.name());
			core.cancelRuleEdit();
			assertEquals(empty, core.buildPreviewDefinition(null, "", true).filter());
		}
	}

	@Test
	void stableLargeExactDraftReusesCompilationAndValidation() {
		List<GroupFilter> filters = java.util.stream.IntStream.range(0, 6322)
			.<GroupFilter>mapToObj(i -> new GroupFilter.ExactStack(new ItemDataPayload(ItemDataPayload.ITEM_COMPONENTS,
				JsonParser.parseString("{\"id\":\"minecraft:stone\",\"count\":" + (i + 1) + "}"))))
			.toList();
		EditorStateCore core = new EditorStateCore(new GroupDefinition("large", "Large", true,
			new GroupFilter.Any(filters)), () -> {});
		int initial = core.validationRuns();
		var compiled = core.buildCurrentFilter();
		for (int i = 0; i < 100; i++) {
			assertSame(compiled, core.buildCurrentFilter());
			assertTrue(core.canSave("Large"));
			assertTrue(core.currentValidationErrors().isEmpty());
		}
		assertEquals(initial, core.validationRuns());
		assertFalse(core.canSave(" "));
		assertTrue(core.canSave("Renamed"));
		core.buildPreviewDefinition("large", "Renamed", false,
			AppearanceDraft.fromIconIds(List.of(), GroupTheme.EMPTY), 7);
		assertSame(compiled, core.buildCurrentFilter());
	}

	@Test
	void validationDetectsUnannouncedEditsAndDoesNotAdvancePreviewFallback() {
		GroupFilter original = new GroupFilter.ExactStack("{\"id\":\"minecraft:stone\"}");
		EditorStateCore core = new EditorStateCore(new GroupDefinition("test", "Test", true, original), () -> {});
		GroupFilterRuleDraft.Node node = core.selectedRuleNode();
		node.setPrimaryValue("{\"id\":\"minecraft:dirt\"}");
		assertTrue(core.canSave("Test"));
		node.setPrimaryValue("[]");
		assertFalse(core.canSave("Test"));
		assertEquals(original, core.buildPreviewDefinition("test", "Test", true).filter());
		List<net.minecraft.network.chat.Component> errors = core.currentValidationErrors();
		((net.minecraft.network.chat.MutableComponent) errors.getFirst()).append("modified");
		assertFalse(core.currentValidationErrors().getFirst().getString().endsWith("modified"));
		node.setPrimaryValue("{\"id\":\"minecraft:dirt\"}");
		assertTrue(core.canSave("Test"));
		GroupFilter updated = core.buildPreviewDefinition("test", "Test", true).filter();
		node.setPrimaryValue("[]");
		assertEquals(updated, core.buildPreviewDefinition("test", "Test", true).filter());
		core.deleteSelectedRule();
		assertFalse(core.canSave("Test"));
		assertTrue(core.currentValidationErrors().isEmpty());
	}

	@Test
	void validationDetectsDirectChildListMutation() {
		EditorStateCore core = new EditorStateCore(new GroupDefinition("test", "Test", true,
			new GroupFilter.Any(List.of(Filters.itemId("minecraft:stone"), Filters.itemId("minecraft:dirt")))), () -> {});
		assertTrue(core.canSave("Test"));
		int initial = core.validationRuns();
		core.selectedRuleNode().children().clear();
		core.currentValidationErrors();
		assertEquals(initial + 1, core.validationRuns());
	}

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
	void insertedRuleTransactionRestoresSelectionOnCancelAndKeepsNodeOnCommit() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		GroupFilterRuleDraft.Node root = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.ALL);

		GroupFilterRuleDraft.Node pending = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.TAG);
		assertTrue(core.hasRuleEditTransaction());
		assertEquals(1, root.children().size());

		core.cancelRuleEdit();
		assertFalse(core.hasRuleEditTransaction());
		root = core.selectedRuleNode();
		assertTrue(root.children().isEmpty());

		GroupFilterRuleDraft.Node kept = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.TAG);
		kept.setPrimaryValue("c:missing");
		core.commitRuleEdit();
		assertFalse(core.hasRuleEditTransaction());
		assertEquals(List.of(kept), root.children());

		core.cancelRuleEdit();
		assertEquals(List.of(kept), root.children());
	}

	@Test
	void existingRuleTransactionRestoresTheWholeTreeAndBlocksSaveUntilCommitted() {
		GroupFilter original = Filters.any(
			Filters.itemId("minecraft:stone"),
			Filters.itemTag("c:ingots"));
		EditorStateCore core = new EditorStateCore(
			new GroupDefinition("test", "Test", true, original), () -> {});
		GroupFilterRuleDraft.Node first = core.selectedRuleNode().children().get(0);

		assertTrue(core.beginRuleEdit(first));
		first.setPrimaryValue("minecraft:dirt");
		first.parent().children().removeLast();
		assertFalse(core.canSave("Test"));
		assertFalse(core.beginRuleEdit(first));

		core.cancelRuleEdit();

		assertEquals(original, core.buildCurrentFilter().orElseThrow());
		assertEquals("minecraft:stone", core.selectedRuleNode().primaryValue());
		assertTrue(core.canSave("Test"));
	}

	@Test
	void transactionDetectsChangesBetweenIncompleteDrafts() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		GroupFilterRuleDraft.Node root = core.insertRuleRelative(GroupFilterRuleDraft.NodeKind.ALL);
		assertTrue(core.beginRuleEdit(root));

		root.setKind(GroupFilterRuleDraft.NodeKind.ANY);

		assertTrue(core.ruleEditChanged());
		core.cancelRuleEdit();
		assertEquals(GroupFilterRuleDraft.NodeKind.ALL, core.selectedRuleNode().kind());
	}

	@Test
	void duplicateContentsMutationDoesNotReplaceTheCanonicalRuleTree() {
		GroupEditorState state = new GroupEditorState(new GroupDefinition(
			"test", "Test", true, Filters.fluidId("minecraft:water")));
		GroupFilterRuleDraft.Node selected = state.selectedRuleNode();

		state.addFluidId("minecraft:water");

		assertSame(selected, state.selectedRuleNode());
		assertEquals(Filters.fluidId("minecraft:water"), state.buildCurrentFilter().orElseThrow());
		state.contentsDraftSnapshot().fluidIds().add("minecraft:lava");
		assertEquals(Filters.fluidId("minecraft:water"), state.buildCurrentFilter().orElseThrow());
	}

	@Test
	void absentFluidRemovalKeepsSingletonAnyRuleIdentityAndSelection() {
		GroupFilter original = new GroupFilter.Any(List.of(Filters.fluidId("minecraft:water")));
		GroupEditorState state = new GroupEditorState(new GroupDefinition("test", "Test", true, original));
		GroupFilter canonical = state.buildCurrentFilter().orElseThrow();
		List<GroupFilterRuleDraft.Node> tree = state.flattenedRuleNodes().stream()
			.map(GroupFilterRuleDraft.FlatNode::node).toList();
		GroupFilterRuleDraft.Node selected = state.selectedRuleNode();
		EditorFluidIngredientView absent = new EditorFluidIngredientView(
			new Object(), Component.literal("Lava"), "minecraft:lava",
			IngredientSearchDocument.of(List.of(), List.of(), Set.of()), null);

		state.removeFluidSelection(absent);

		assertEquals(canonical, state.buildCurrentFilter().orElseThrow());
		assertSame(selected, state.selectedRuleNode());
		assertEquals(tree, state.flattenedRuleNodes().stream().map(GroupFilterRuleDraft.FlatNode::node).toList());
	}

	@Test
	void absentGenericRemovalKeepsSingletonAnyRuleIdentityAndSelection() {
		GroupFilter original = new GroupFilter.Any(List.of(
			Filters.id("emi:mekanism_chemical", "mekanism:oxygen")));
		GroupEditorState state = new GroupEditorState(new GroupDefinition("test", "Test", true, original));
		GroupFilter canonical = state.buildCurrentFilter().orElseThrow();
		List<GroupFilterRuleDraft.Node> tree = state.flattenedRuleNodes().stream()
			.map(GroupFilterRuleDraft.FlatNode::node).toList();
		GroupFilterRuleDraft.Node selected = state.selectedRuleNode();
		EditorGenericIngredientView absent = new EditorGenericIngredientView(
			"emi:mekanism_chemical", new Object(), new Object(), Component.literal("Hydrogen"),
			"mekanism:hydrogen", "mekanism:hydrogen", Set.of(),
			IngredientSearchDocument.of(List.of(), List.of(), Set.of()));

		state.removeGenericSelection(absent);

		assertEquals(canonical, state.buildCurrentFilter().orElseThrow());
		assertSame(selected, state.selectedRuleNode());
		assertEquals(tree, state.flattenedRuleNodes().stream().map(GroupFilterRuleDraft.FlatNode::node).toList());
	}

	@Test
	void pendingRootCancelClearsTree() {
		EditorStateCore core = new EditorStateCore(null, () -> {});
		core.beginInsertRule(GroupFilterRuleDraft.NodeKind.TAG);
		assertTrue(core.hasRulesRoot());

		core.cancelRuleEdit();

		assertFalse(core.hasRulesRoot());
		assertFalse(core.hasRuleEditTransaction());
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

		assertEquals(List.of(GroupIconDefinition.item("minecraft:emerald"),
			GroupIconDefinition.item("minecraft:gold_ingot")), preview.iconIds());
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
			new GroupFilter.ExactStack(new com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload("minecraft:item_components", com.google.gson.JsonParser.parseString("{\"id\":\"minecraft:stone\"}"))),
			new GroupFilter.ExactStack(new com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload("minecraft:item_components", com.google.gson.JsonParser.parseString("{\"id\":\"minecraft:oak_boat\"}")))
		), filter.children());
	}
}
