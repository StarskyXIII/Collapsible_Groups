package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.group.*;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.viewer.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class GroupManagerCardAssemblerTest {
    @Test void coldDisplayNeverRequestsPreviewResolution() {
        var group = group("cold", "minecraft:stone");
        var display = new ViewerGroupDisplaySnapshot(null, id -> {
            throw new AssertionError("Unindexed definitions must not resolve previews");
        }, new CompletableFuture<>(), true, false);
        var result = GroupManagerCardAssembler.build(repository(List.of(group)), display);
        assertTrue(result.generationPending());
        assertEquals(GroupEvaluation.Status.PENDING, result.cards().getFirst().evaluation().status());
        assertEquals(0, result.cards().getFirst().entryCount());
    }

    @Test void localEditRetainsUnchangedEmptyEvaluationAndOnlyChangedDefinitionWaits() {
        var unchanged = group("empty", "minecraft:stone");
        var old = group("edited", "minecraft:stone");
        var changed = group("edited", "minecraft:dirt");
        var display = display(List.of(unchanged, old));
        var result = GroupManagerCardAssembler.build(repository(List.of(unchanged.withEnabled(false), changed)), display);
        assertTrue(result.cards().getFirst().evaluation().empty());
        assertFalse(result.cards().getFirst().group().enabled());
        assertEquals(GroupEvaluation.Status.PENDING, result.cards().get(1).evaluation().status());
    }

    @Test void capturedRepositorySourceSurvivesLaterLiveRepositoryState() {
        var group = group("custom-looking-script-id", "minecraft:stone");
        var repository = new GroupRepository.ReadSnapshot(List.of(group), GroupResourceData.empty(), true,
            Map.of(group.id(), GroupSource.KUBEJS));
        var result = GroupManagerCardAssembler.build(repository, display(List.of(group)));
        assertEquals(com.starskyxiii.collapsible_groups.client.manager.model.GroupSource.KUBEJS,
            result.cards().getFirst().source());
        assertFalse(result.cards().getFirst().editable());
    }

    @Test void screenChangesCannotMutatePublishedCards() {
        var first = group("first", "minecraft:stone");
        var second = group("second", "minecraft:stone");
        var result = GroupManagerCardAssembler.build(repository(List.of(first, second)), display(List.of(first, second)));
        var working = new ArrayList<>(result.cards());
        working.set(0, working.getFirst().withGroup(first.withEnabled(false)));
        working.remove(1);
        assertEquals(2, result.cards().size());
        assertTrue(result.cards().getFirst().group().enabled());
        assertThrows(UnsupportedOperationException.class, () -> result.cards().clear());
    }

    private static GroupRepository.ReadSnapshot repository(List<GroupDefinition> groups) {
        return new GroupRepository.ReadSnapshot(groups, GroupResourceData.empty(), true,
            groups.stream().collect(java.util.stream.Collectors.toMap(GroupDefinition::id, g -> GroupSource.USER)));
    }

    private static ViewerGroupDisplaySnapshot display(List<GroupDefinition> groups) {
        var definitions = groups.stream().collect(java.util.stream.Collectors.toMap(GroupDefinition::id, g -> g));
        var evaluations = groups.stream().collect(java.util.stream.Collectors.toMap(GroupDefinition::id,
            g -> new GroupEvaluation(1, GroupEvaluation.Status.COMPLETE, 0, 0, 0, List.of())));
        return new ViewerGroupDisplaySnapshot(new GroupCandidateIndex(Map.of(), definitions, 0, 0, 0, evaluations, 1),
            id -> Optional.of(new ViewerGroupPreviewSnapshot(List.of(), List.of(), List.of())),
            new CompletableFuture<>(), true, false);
    }

    private static GroupDefinition group(String id, String item) {
        return new GroupDefinition(id, id, true, new GroupFilter.Id("item", item));
    }
}
