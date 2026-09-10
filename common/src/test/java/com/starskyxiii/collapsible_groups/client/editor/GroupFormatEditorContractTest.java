package com.starskyxiii.collapsible_groups.client.editor;

import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupDocumentFormat;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GroupFormatEditorContractTest {
	@BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

	@Test void legacyRuleInsertionAndContentsInjectionCannotBypassPolicy() {
		GroupDefinition legacy = GroupDefinition.of("legacy", "Legacy", Filters.itemId("minecraft:dirt"));
		EditorStateCore core = new EditorStateCore(legacy, () -> {});
		for (var kind : List.of(GroupFilterRuleDraft.NodeKind.NBT, GroupFilterRuleDraft.NodeKind.NBT_PATH,
			GroupFilterRuleDraft.NodeKind.EXACT_STACK)) {
			assertFalse(core.canAddRuleKind(kind));
			assertNull(core.insertRuleRelative(kind));
			assertNull(core.beginInsertRule(kind));
			assertFalse(core.hasRuleEditTransaction());
		}
		core.setContentsQuickEditAvailable(true);
		var injected = GroupFilterEditorDraft.empty();
		injected.explicitItemSelectors().add(GroupItemSelector.exactSelector(tagged()));
		core.syncRulesFromContentsDraft(injected);
		assertEquals(legacy.filter(), core.buildCurrentFilter().orElseThrow());
		assertTrue(core.canSave("Renamed"));
	}

	@Test void legacyOrdinaryAddRemoveWorksWhileExactAddAndVariantSplitAreBlocked() {
		GroupEditorState state = new GroupEditorState(GroupDefinition.of("legacy", "Legacy", Filters.itemId("minecraft:dirt")));
		ItemStack stone = new ItemStack(Items.STONE);
		assertTrue(state.addSingleSelectionIfAbsent(stone));
		assertTrue(state.isWholeItemSelected(stone));
		state.removeSingleSelection(stone, List.of(stone));
		assertFalse(state.isWholeItemSelected(stone));
		assertFalse(state.addSingleSelectionIfAbsent(tagged()));
		assertTrue(state.formatActionBlocked());
		state.toggleSingleSelection(tagged());
		assertFalse(state.isExactSelected(tagged()));
		state.toggleWholeItemSelection(stone);
		assertTrue(state.isWholeItemSelected(stone));
		state.removeSingleSelection(stone, List.of(stone, tagged()));
		assertTrue(state.isWholeItemSelected(stone));
		assertTrue(state.formatActionBlocked());
		assertFalse(JsonParser.parseString(GroupConfig.toJson(state.buildPreviewDefinition())).getAsJsonObject().has("schema_version"));
	}

	@Test void semanticExactSelectionHighlightsTogglesAndDeduplicatesDifferentSnbt() {
		var helper = new EditorItemSelectionHelper();
		ItemStack stack = tagged();
		Set<String> selected = new LinkedHashSet<>(List.of("stack:{tag:{value:1b},Count:64b,id:'minecraft:stone'}"));
		for (int i = 0; i < 1000; i++) assertTrue(helper.isExactSelected(stack, selected));
		assertFalse(helper.addSingleSelectionIfAbsent(stack, selected));
		assertEquals(1, selected.size());
		helper.toggleSingleSelection(stack, selected);
		assertTrue(selected.isEmpty());
		helper.addSingleSelectionIfAbsent(stack, selected);
		assertTrue(helper.isExactSelected(stack, selected));
		helper.removeSingleSelection(stack, List.of(stack), selected);
		assertTrue(selected.isEmpty());
	}

	@Test void typedExactPayloadSurvivesContentsAndRulesTransactions() {
		GroupFilter.ExactStack filter = new GroupFilter.ExactStack(ItemDataPayload.nbt("{tag:{value:1b},Count:64b,id:'minecraft:stone'}"));
		GroupDefinition group = GroupDefinition.of("v1", "V1", filter);
		var flat = GroupFilterEditorDraft.decode(filter).draft();
		flat.itemTags().add("minecraft:logs");
		assertEquals(filter, ((GroupFilter.Any) flat.toFilter().orElseThrow()).children().get(0));
		EditorStateCore core = new EditorStateCore(group, () -> {});
		var selected = core.selectedRuleNode();
		assertTrue(core.beginRuleEdit(selected));
		selected.setPrimaryValue("{id:'minecraft:dirt',Count:1b}");
		core.cancelRuleEdit();
		assertEquals(filter, core.buildCurrentFilter().orElseThrow());
		assertEquals(GroupDocumentFormat.V1, core.buildPreviewDefinition("v1", "Renamed", true).documentFormat());
		GroupEditorState fresh = new GroupEditorState(null);
		fresh.toggleSingleSelection(tagged());
		var captured = assertInstanceOf(GroupFilter.ExactStack.class, fresh.buildCurrentFilter().orElseThrow());
		assertNotNull(captured.payload());
		assertEquals(ItemDataPayload.NBT, captured.payload().dataFormat());
	}

    @Test void unavailableTypedPayloadCannotBeReinterpretedByPublicRuleDraftDecode() {
        ItemDataPayload foreign = new ItemDataPayload(ItemDataPayload.ITEM_COMPONENTS, JsonParser.parseString("{\"id\":\"minecraft:stone\",\"Count\":1}"));
        GroupFilter malformed = new GroupFilter.ExactStack(new ItemDataPayload(ItemDataPayload.NBT, JsonParser.parseString("{}")));
        assertThrows(IllegalArgumentException.class, () -> GroupFilterRuleDraft.decode(malformed));
        for (GroupFilter leaf : List.of(new GroupFilter.ExactStack(foreign), new GroupFilter.Nbt(foreign),
            new GroupFilter.NbtPath("value", foreign))) {
            for (GroupFilter filter : List.of(leaf, Filters.not(Filters.all(Filters.itemId("minecraft:stone"), leaf)))) {
                assertThrows(IllegalArgumentException.class, () -> GroupFilterRuleDraft.decode(filter));
                GroupDefinition group = GroupDefinition.of("foreign", "Foreign", filter);
                assertTrue(group.hasUnavailableFilter());
                EditorStateCore core = new EditorStateCore(group, () -> {});
                assertEquals(filter, core.buildCurrentFilter().orElseThrow());
                assertFalse(core.beginRuleEdit(core.selectedRuleNode()));
                core.cancelRuleEdit();
                assertEquals(filter, core.buildCurrentFilter().orElseThrow());
                GroupDefinition copy = core.buildPreviewDefinition("copy", "Renamed", true);
                assertEquals(filter, copy.filter());
                assertTrue(copy.hasUnavailableFilter());
            }
        }
    }

	private static ItemStack tagged() {
		ItemStack stack = new ItemStack(Items.STONE, 5);
		CompoundTag tag = new CompoundTag();
		tag.putByte("value", (byte) 1);
		stack.setTag(tag);
		return stack;
	}
}
