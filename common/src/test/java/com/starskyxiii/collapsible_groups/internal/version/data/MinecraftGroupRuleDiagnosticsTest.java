package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import com.starskyxiii.collapsible_groups.viewer.GroupEvaluationContext;
import com.starskyxiii.collapsible_groups.viewer.GroupProjectionEngine;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftGroupRuleDiagnosticsTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void validNativeDataWithNoCandidatesIsCompleteAndEmpty() {
        for (GroupFilter filter : List.of(new GroupFilter.Nbt(ItemDataPayload.nbt("{marker:1b}")),
            new GroupFilter.NbtPath("marker", ItemDataPayload.nbt("1b")),
            new GroupFilter.ExactStack(ItemDataPayload.nbt("{id:'minecraft:stone',Count:1b}")))) {
            var result = evaluate(group(filter), List.of());
            assertEquals(GroupEvaluation.Status.COMPLETE, result.status());
            assertTrue(result.empty());
        }
    }

    @Test void invalidNativeDataIsReportedUnderEveryOperatorWithoutCandidates() {
        for (GroupFilter invalid : List.of(new GroupFilter.Nbt("{broken:"),
            new GroupFilter.NbtPath("marker[", "1b"), new GroupFilter.NbtPath("marker", "[broken"),
            new GroupFilter.ExactStack("{id:'minecraft:stone'}"),
            new GroupFilter.ExactStack("{id:'minecraft:stone',Count:0b}"))) {
            assertEquals(GroupEvaluation.Status.ERROR, new MinecraftGroupRuleDiagnostics().apply(invalid).get(0).status());
            GroupFilter persisted = parsedNative(invalid);
            for (GroupFilter filter : List.of(Filters.any(Filters.itemId("minecraft:stone"), persisted),
                Filters.all(Filters.itemId("minecraft:stone"), persisted), Filters.not(persisted))) {
                var result = evaluate(group(filter), List.of());
                assertEquals(GroupEvaluation.Status.ERROR, result.status(), invalid.toString());
                assertFalse(result.empty());
            }
        }
    }

    @Test void missingItemAndForeignPayloadsRemainUnavailable() {
        for (GroupFilter filter : List.of(
            new GroupFilter.ExactStack(ItemDataPayload.nbt("{id:'missing_mod:item',Count:1b}")),
            new GroupFilter.ExactStack(new ItemDataPayload(ItemDataPayload.ITEM_COMPONENTS, new JsonObject())),
            new GroupFilter.HasComponent("minecraft:custom_data", "{}"),
            new GroupFilter.ComponentPath("minecraft:custom_data", "marker", "1"))) {
            var result = evaluate(group(Filters.not(filter)), List.of());
            assertEquals(GroupEvaluation.Status.UNAVAILABLE, result.status());
            assertFalse(result.empty());
        }
    }

    @Test void nonStringNativePayloadCannotBeHiddenAsAnEmptyGroup() {
        JsonObject data = new JsonObject();
        data.addProperty("id", "minecraft:stone");
        data.addProperty("Count", 1);
        var payload = new ItemDataPayload(ItemDataPayload.NBT, data);
        for (GroupFilter filter : List.of(new GroupFilter.Nbt(payload), new GroupFilter.NbtPath("marker", payload),
            new GroupFilter.ExactStack(payload))) {
            var result = evaluate(group(filter), List.of());
            assertEquals(GroupEvaluation.Status.ERROR, result.status());
            assertFalse(result.empty());
            assertTrue(result.issues().stream().anyMatch(issue -> issue.reason().contains("SNBT string")));
        }
    }

    @Test void parsedForeignDataDiffersFromMalformedNativeData() {
        String document = """
            {"schema_version":1,"id":"test","filter":{"type":"item","stack":%s}}
            """;
        var foreign = GroupConfig.fromJsonChecked(document.formatted(
            "{\"data_format\":\"minecraft:item_components\",\"data\":{\"id\":\"minecraft:stone\",\"count\":1}}"));
        var malformed = GroupConfig.fromJsonChecked(document.formatted(
            "{\"data_format\":\"minecraft:nbt\",\"data\":\"{broken:\"}"));
        assertEquals(GroupEvaluation.Status.UNAVAILABLE, evaluate(foreign, List.of()).status());
        assertEquals(GroupEvaluation.Status.ERROR, evaluate(malformed, List.of()).status());
    }

    @Test void aMatchingOrBranchCannotHideAnInvalidNativeRule() {
        var group = group(Filters.any(Filters.itemId("minecraft:stone"), parsedNative(new GroupFilter.Nbt("{broken:"))));
        var result = evaluate(group, List.of(new ItemStack(Items.STONE)));
        assertEquals(1, result.count());
        assertEquals(GroupEvaluation.Status.ERROR, result.status());
        assertFalse(result.empty());
    }

    @Test void nativeNbtCountsOneMatchWithNumericTypeFidelity() throws Exception {
        ItemStack stone = new ItemStack(Items.STONE);
        stone.setTag(TagParser.parseTag("{marker:1b}"));
        var matching = evaluate(group(new GroupFilter.NbtPath("marker", ItemDataPayload.nbt("1b"))), List.of(stone));
        var differentType = evaluate(group(new GroupFilter.NbtPath("marker", ItemDataPayload.nbt("1"))), List.of(stone));
        assertEquals(GroupEvaluation.Status.COMPLETE, matching.status());
        assertEquals(1, matching.count());
        assertTrue(matching.hasContent());
        assertEquals(GroupEvaluation.Status.COMPLETE, differentType.status());
        assertTrue(differentType.empty());
    }

    private static GroupDefinition group(GroupFilter filter) {
        return new GroupDefinition("test", "Test", true, filter);
    }

    private static GroupFilter parsedNative(GroupFilter filter) {
        JsonObject raw = new JsonObject();
        raw.addProperty("type", "item");
        if (filter instanceof GroupFilter.Nbt nbt) raw.add("nbt", ItemDataPayload.nbt(nbt.expectedSnbt()).toJson());
        if (filter instanceof GroupFilter.NbtPath path) {
            raw.addProperty("nbt_path", path.path());
            raw.add("value", ItemDataPayload.nbt(path.expectedSnbt()).toJson());
        }
        if (filter instanceof GroupFilter.ExactStack exact) raw.add("stack", ItemDataPayload.nbt(exact.encodedStack()).toJson());
        JsonObject document = new JsonObject();
        document.addProperty("schema_version", 1);
        document.addProperty("id", "test");
        document.add("filter", raw);
        return GroupConfig.fromJsonChecked(document.toString()).filter();
    }

    private static GroupEvaluation evaluate(GroupDefinition group, List<ItemStack> stacks) {
        var ingredients = stacks.stream().map(stack -> new ViewerIngredient<>(
            new ViewerIngredientIdentity("item", stack.toString()), ViewerIngredient.Kind.ITEM,
            stack, new ItemStackIngredientView(stack))).toList();
        var context = new GroupEvaluationContext(Set.of("item"), Map.of(), new MinecraftGroupRuleDiagnostics());
        return GroupProjectionEngine.buildCandidateIndex(new ViewerIngredientUniverse<>(ingredients, null, context),
            List.of(group)).evaluations().get(group.id());
    }
}
