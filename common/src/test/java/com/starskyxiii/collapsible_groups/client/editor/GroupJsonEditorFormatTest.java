package com.starskyxiii.collapsible_groups.client.editor;

import com.google.gson.*;
import com.starskyxiii.collapsible_groups.group.*;
import com.starskyxiii.collapsible_groups.group.filter.*;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GroupJsonEditorFormatTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void blankDraftUsesStrictLiteralsAndInvalidInputCannotApplyOrSave() {
        EditorStateCore core = new EditorStateCore(null, () -> {});
        GroupFilterRuleDraft.Node node = core.beginInsertRule(GroupFilterRuleDraft.NodeKind.HAS_COMPONENT);
        assertTrue(node.typedData());
        node.setPrimaryValue("minecraft:damage");
        node.setSecondaryValue("1 trailing");
        assertFalse(node.hasValidDataLiteral());
        assertTrue(core.buildCurrentFilter().isEmpty());
        core.commitRuleEdit();
        assertTrue(core.hasRuleEditTransaction());
        assertFalse(core.canSave("Group"));
        assertEquals("1 trailing", node.secondaryValue());
        node.setSecondaryValue("\"1\"");
        assertTrue(node.hasValidDataLiteral());
        core.commitRuleEdit();
        assertFalse(core.hasRuleEditTransaction());
        GroupFilter.HasComponent string = assertInstanceOf(GroupFilter.HasComponent.class, core.buildCurrentFilter().orElseThrow());
        assertEquals(new JsonPrimitive("1"), string.payload().data());
        assertTrue(core.canSave("Group"));
        assertTrue(core.beginRuleEdit(node));
        node.setSecondaryValue("1");
        assertEquals(new JsonPrimitive(1), ((GroupFilter.HasComponent) core.buildCurrentFilter().orElseThrow()).payload().data());
        core.cancelRuleEdit();
        assertEquals(string, core.buildCurrentFilter().orElseThrow());
        assertEquals(GroupDocumentFormat.V1, core.buildPreviewDefinition("new", "Group", true).documentFormat());
    }

    @Test void loadedLegacyComponentDraftKeepsOriginalStringInterpretation() {
        GroupDefinition source = new GroupDefinition("legacy", "Legacy", true, Filters.itemComponent("minecraft:damage", "1"));
        EditorStateCore core = new EditorStateCore(source, true, () -> {});
        GroupFilterRuleDraft.Node node = core.selectedRuleNode();
        assertFalse(node.typedData());
        assertTrue(core.beginRuleEdit(node));
        node.setSecondaryValue("2");
        core.cancelRuleEdit();
        assertEquals(source.filter(), core.buildCurrentFilter().orElseThrow());
        GroupDefinition preview = core.buildPreviewDefinition("copy", "Copy", true);
        assertEquals(GroupDocumentFormat.LEGACY, preview.documentFormat());
        assertFalse(JsonParser.parseString(GroupConfig.toJson(preview)).getAsJsonObject().has("schema_version"));
    }

    @Test void contentsChangesPreserveTypedComponentsAndPromoteNewExactSelectionsInBlankGroups() {
        GroupDefinition source = new GroupDefinition("typed", "Typed", true,
            Filters.itemComponentPathValue("minecraft:custom_data", "n", new JsonPrimitive("1")));
        GroupEditorState state = new GroupEditorState(source);
        ItemStack named = new ItemStack(Items.STONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
        state.toggleSingleSelection(named);
        GroupFilter.Any any = assertInstanceOf(GroupFilter.Any.class, state.buildCurrentFilter().orElseThrow());
        assertEquals(source.filter(), any.children().getFirst());
        GroupFilter.ExactStack exact = assertInstanceOf(GroupFilter.ExactStack.class, any.children().getLast());
        assertNotNull(exact.payload());
        assertEquals(ItemDataPayload.ITEM_COMPONENTS, exact.payload().dataFormat());
        GroupFilter roundTrip = state.contentsDraftSnapshot().toFilter().orElseThrow();
        assertEquals(any, roundTrip);
    }

    @Test void exactSelectionUsesItemEquivalenceAcrossJsonSpellingsAndCounts() {
        ItemStack reference = new ItemStack(Items.STONE);
        reference.set(DataComponents.CUSTOM_NAME, Component.literal("chosen"));
        String encoded = com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector.exactSelector(reference);
        JsonObject nativeJson = JsonParser.parseString(encoded.substring("stack:".length())).getAsJsonObject();
        nativeJson.addProperty("count", 32);
        Set<String> selected = new LinkedHashSet<>(List.of("stack:" + new GsonBuilder().setPrettyPrinting().create().toJson(nativeJson)));
        EditorItemSelectionHelper helper = new EditorItemSelectionHelper();
        assertTrue(helper.isExactSelected(reference, selected));
        assertTrue(helper.hasPreferredSelection(reference, selected));
        assertFalse(helper.addSingleSelectionIfAbsent(reference, selected));
        assertEquals(1, selected.size());
        helper.toggleSingleSelection(reference, selected);
        assertTrue(selected.isEmpty());
        helper.addSingleSelectionIfAbsent(reference, selected);
        assertTrue(helper.isExactSelected(reference.copyWithCount(9), selected));
        ItemStack different = reference.copy();
        different.set(DataComponents.CUSTOM_NAME, Component.literal("different"));
        assertFalse(helper.isExactSelected(different, selected));
        helper.removeSingleSelection(reference, List.of(reference, different), selected);
        assertTrue(selected.isEmpty());
    }

    @Test void unsupportedTypedLeavesCannotBeReinterpretedThroughDraftsOrEditorCopies() {
        ItemDataPayload foreign = new ItemDataPayload(ItemDataPayload.NBT, JsonParser.parseString("{\"id\":\"minecraft:stone\"}"));
        GroupFilter malformed = new GroupFilter.ExactStack(new ItemDataPayload(ItemDataPayload.ITEM_COMPONENTS, new JsonPrimitive("invalid")));
        assertThrows(IllegalArgumentException.class, () -> GroupFilterRuleDraft.decode(malformed, GroupDocumentFormat.V1));
        assertThrows(IllegalArgumentException.class, () -> new GroupDefinition("invalid", "Invalid", true, malformed));
        for (GroupFilter leaf : List.of(new GroupFilter.ExactStack(foreign),
            new GroupFilter.HasComponent("minecraft:damage", foreign),
            new GroupFilter.ComponentPath("minecraft:custom_data", "n", foreign))) {
            for (GroupFilter filter : List.of(leaf, Filters.not(Filters.all(Filters.itemId("minecraft:stone"), leaf)))) {
                assertThrows(IllegalArgumentException.class, () -> GroupFilterRuleDraft.decode(filter));
                assertThrows(IllegalArgumentException.class, () -> GroupFilterRuleDraft.decode(filter, GroupDocumentFormat.V1));
                GroupDefinition group = new GroupDefinition("foreign", "Foreign", true, filter);
                assertTrue(group.hasUnavailableFilter());
                assertFalse(group.isStructurallyEditable());
                EditorStateCore core = new EditorStateCore(group, true, () -> {});
                assertEquals(filter, core.buildCurrentFilter().orElseThrow());
                assertFalse(core.beginRuleEdit(core.selectedRuleNode()));
                core.cancelRuleEdit();
                assertEquals(filter, core.buildCurrentFilter().orElseThrow());
                GroupDefinition copy = core.buildPreviewDefinition("copy", "Renamed", true);
                assertEquals(filter, copy.filter());
                assertTrue(copy.hasUnavailableFilter());
                assertEquals(JsonParser.parseString(GroupConfig.toJson(group)).getAsJsonObject().get("filter"),
                    JsonParser.parseString(GroupConfig.toJson(copy)).getAsJsonObject().get("filter"));
                assertTrue(GroupConfig.fromJson(GroupConfig.toJson(copy)).hasUnavailableFilter());
            }
        }
    }

    @Test void unknownDocumentCannotSaveFromEditorOrBecomeSupportedInCopyPreview() {
        GroupDefinition source = GroupConfig.fromJson("{\"id\":\"future\",\"schema_version\":null,\"future\":null}");
        EditorStateCore core = new EditorStateCore(source, true, () -> {});
        assertFalse(core.canSave("Copy"));
        GroupDefinition preview = core.buildPreviewDefinition("copy", "Copy", true);
        assertEquals(GroupDocumentFormat.UNSUPPORTED, preview.documentFormat());
        assertEquals(source.rawDocument(), preview.rawDocument());
        assertTrue(preview.hasUnavailableFilter());
    }
}
