package com.starskyxiii.collapsible_groups.client.manager;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CategoryMoveStateTest {
    private static CategoryChoices.Entry entry(String id, String name) {
        return new CategoryChoices.Entry(id, Component.literal(name));
    }

    @Test void searchingAwayFromSelectionNeverSelectsAnotherResult() {
        var state = new CategoryMoveState(List.of("group"));
        state.entries(List.of(entry("a", "Alpha"), entry("b", "Beta")));
        state.select("a");
        state.search("beta");
        assertNull(state.selected());
        assertFalse(state.canMove());
        assertEquals("b", state.visible().get(0).id());
        state.search("missing");
        assertTrue(state.visible().isEmpty());
        state.search("");
        assertNull(state.selected());
    }

    @Test void translatedNameCollisionsKeepDistinctIdentitiesAndRemovedCategoriesClearSelection() {
        var state = new CategoryMoveState(List.of("group"));
        state.entries(List.of(entry("source:a", "Same"), entry("source:b", "Same")));
        state.select("source:b");
        state.entries(List.of(entry("source:b", "Renamed"), entry("source:a", "Same")));
        assertEquals("source:b", state.selected());
        state.entries(List.of(entry("source:a", "Same")));
        assertNull(state.selected());
    }

    @Test void changedTargetsRequireAnotherConfirmationAndNeverAddNewGroups() {
        var state = new CategoryMoveState(List.of("g1", "g2", "g1"));
        state.entries(List.of(entry("target", "Target")));
        state.select("target");
        assertEquals(CategoryMoveState.Validation.TARGETS_CHANGED, state.validate(Set.of("g1", "g3")));
        assertEquals(List.of("g1"), state.targets());
        assertEquals(CategoryMoveState.Validation.READY, state.validate(Set.of("g1", "g3")));
        assertEquals(CategoryMoveState.Validation.TARGETS_CHANGED, state.validate(Set.of()));
        assertFalse(state.canMove());
        assertEquals(CategoryMoveState.Validation.UNAVAILABLE, state.validate(Set.of("g1")));
    }
}
