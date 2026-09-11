package com.starskyxiii.collapsible_groups.group;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.viewer.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinGlobalGateTest {
    @AfterEach void resetRepository() { GroupRepository.replaceForTesting(List.of()); }

    @Test void catalogMembershipSurvivesOverridesAndNewIdsRemainIndependent() {
        var data = GroupResourceLoader.assemble(List.of(
            GroupResourceLoaderTest.layer(GroupResourceLoaderTest.doc(GroupSource.BUILTIN, "builtin", "plain_id", "minecraft:stone", true, 0)),
            GroupResourceLoaderTest.layer(GroupResourceLoaderTest.doc(GroupSource.RESOURCE_PACK, "pack", "plain_id", "minecraft:stone", true, 0),
                GroupResourceLoaderTest.doc(GroupSource.RESOURCE_PACK, "pack-copy", "new_id", "minecraft:stone", true, -1))));
        GroupRepository.replaceResourcesForTesting(data, false);
        GroupDefinition override = GroupRepository.findById("plain_id").orElseThrow();
        GroupDefinition copy = GroupRepository.findById("new_id").orElseThrow();
        GroupDefinition script = new GroupDefinition("__default_not_in_catalog", "Script", true, Filters.itemId("minecraft:stone")).withPriority(-2);
        GroupRepository.setScriptedGroups(List.of(script));
        assertTrue(GroupRepository.isBuiltin("plain_id"));
        assertEquals(GroupSource.RESOURCE_PACK, GroupRepository.sourceOf("plain_id"));
        assertFalse(GroupRepository.isActive(override));
        assertTrue(override.enabled());
        assertTrue(GroupRepository.isActive(copy));
        assertTrue(GroupRepository.isActive(script));
        var ingredient = new ViewerIngredient<>(new ViewerIngredientIdentity("item", "minecraft:stone"), ViewerIngredient.Kind.ITEM,
            "stone", new IngredientView() {
                public String ingredientType() { return "item"; }
                public ResourceLocation resourceLocation() { return ResourceLocation.parse("minecraft:stone"); }
                public boolean hasTag(ResourceLocation tag) { return false; }
                public boolean matchesExactStack(String stack) { return false; }
            });
        var groups = GroupRepository.getAllIncludingScripted();
        var candidates = GroupProjectionEngine.buildCandidateIndex(new ViewerIngredientUniverse<>(List.of(ingredient)), groups);
        assertEquals("new_id", GroupProjectionEngine.resolveOwnership(candidates, groups).get(ingredient.identity()));
        assertEquals(1, candidates.evaluations().get("plain_id").count());
        GroupRepository.replaceResourcesForTesting(data, true);
        assertTrue(GroupRepository.isActive(GroupRepository.findById("plain_id").orElseThrow()));
        assertEquals("plain_id", GroupProjectionEngine.resolveOwnership(candidates, GroupRepository.getAllIncludingScripted()).get(ingredient.identity()));
        assertEquals(1, candidates.evaluations().get("plain_id").count());
    }

    @Test void sourceReplacementKeepsIndependentPerGroupPreferenceWhenGlobalGateChanges() {
        var data = GroupResourceLoader.assemble(List.of(GroupResourceLoaderTest.layer(
            GroupResourceLoaderTest.doc(GroupSource.BUILTIN, "builtin", "group", "minecraft:stone", true, 0))));
        GroupService service = new GroupService();
        service.replaceManaged(data, Map.of("group", false), false);
        assertFalse(service.findById("group").orElseThrow().enabled());
        service.setBuiltinsEnabled(true);
        assertFalse(service.findById("group").orElseThrow().enabled());
        assertSame(data, service.resources());
        service.setBuiltinsEnabled(false);
        assertFalse(service.findById("group").orElseThrow().enabled());
        assertSame(data, service.resources());
    }
}
