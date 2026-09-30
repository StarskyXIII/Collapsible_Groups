package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.viewer.GroupProjectionEngine;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class JeiProjectableSnapshotTest {
    @Test void unregisterRuntimeRevokesProjectionAndLateWorkerCannotRestoreIt() throws Exception {
        var index = JeiViewerGroupIndex.instance();
        index.reset();
        var context = context();
        var group = group("stable");
        var built = generation(context, List.of(group));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        index.publishGeneration(built);
        index.configureRebuild(() -> {
            entered.countDown();
            try { assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (InterruptedException error) { throw new AssertionError(error); }
            return built;
        }, Runnable::run, () -> {});
        try {
            index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(group));
            var completion = index.whenReady();
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(index.projectableSnapshot().isPresent());
            JeiViewerAdapter.unregisterRuntime();
            assertTrue(index.projectableSnapshot().isEmpty());
            release.countDown();
            completion.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(index.projectableSnapshot().isEmpty());
            assertFalse(index.ready());
        } finally { release.countDown(); index.reset(); }
    }

    @Test void pendingSubsetKeepsCurrentMetadataAndSurvivesFailureButNotSourceInvalidation() {
        var work = new ArrayDeque<Runnable>();
        var index = new JeiViewerGroupIndex(work::add);
        var context = context();
        var stable = group("stable");
        var changed = group("changed");
        index.publishGeneration(generation(context, List.of(stable, changed)));
        var result = new AtomicReference<>(generation(context, List.of(stable)));
        index.configureRebuild(result::get, Runnable::run, () -> {});
        var renamed = stable.withIconIds(List.of(com.starskyxiii.collapsible_groups.group.GroupIconDefinition.item("test:icon")));
        var edited = changed.withFilter(new GroupFilter.Id("item", "test:two"));
        index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(renamed, edited));
        assertFalse(index.ready());
        assertTrue(index.readyGenerationSnapshot().isEmpty());
        var pending = index.projectableSnapshot().orElseThrow();
        assertEquals(List.of(renamed), pending.groups());
        assertSame(context, pending.projectionContext());
        index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(renamed));
        result.set(generation(context, List.of(renamed)));
        work.remove().run();
        assertFalse(index.ready());
        assertEquals(List.of(renamed), index.projectableSnapshot().orElseThrow().groups());
        work.remove().run();
        assertTrue(index.ready());
        index.configureRebuild(() -> { throw new IllegalStateException("worker"); }, Runnable::run, () -> {});
        index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(renamed));
        work.remove().run();
        assertFalse(index.ready());
        assertTrue(index.whenReady().isCompletedExceptionally());
        assertEquals(List.of(renamed), index.projectableSnapshot().orElseThrow().groups());
        index.onGroupChange(GroupChangeEvent.Kind.SOURCE_RELOAD, List.of(renamed));
        assertTrue(index.projectableSnapshot().isEmpty());
        index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(renamed));
        assertTrue(index.projectableSnapshot().isEmpty());
        index.reset();
        work.remove().run();
        assertTrue(index.projectableSnapshot().isEmpty());
    }

    @Test void sourceTokenAndKubeJsReplacementImmediatelyRevokeProjection() {
        var index = new JeiViewerGroupIndex(Runnable::run);
        var context = context();
        index.publishGeneration(generation(context, List.of(group("stable"))));
        assertTrue(index.projectableSnapshot().isPresent());
        index.updateUniverse(new ViewerIngredientUniverse<>(List.of()));
        assertTrue(index.projectableSnapshot().isEmpty());
        index.publishGeneration(generation(context, List.of(group("stable"))));
        index.onGroupChange(GroupChangeEvent.Kind.KUBEJS_REPLACE, List.of(group("stable")));
        assertTrue(index.projectableSnapshot().isEmpty());
        index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(group("stable")));
        assertTrue(index.projectableSnapshot().isEmpty());
    }

    private static GroupDefinition group(String id) { return new GroupDefinition(id, id, true, new GroupFilter.Id("item", "test:one")); }
    private static JeiViewerAdapter.ProjectionContext context() {
        var manager = (IIngredientManager) Proxy.newProxyInstance(IIngredientManager.class.getClassLoader(),
            new Class<?>[]{IIngredientManager.class}, (proxy, method, args) -> { throw new AssertionError(method); });
        return new JeiViewerAdapter.ProjectionContext(manager, new ViewerIngredientUniverse<ITypedIngredient<?>>(List.of()), Map.of(), List.of());
    }
    private static JeiViewerGroupIndex.Generation generation(JeiViewerAdapter.ProjectionContext context, List<GroupDefinition> groups) {
        return new JeiViewerGroupIndex.Generation(GroupProjectionEngine.buildCandidateIndex(context.universe(), groups),
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), context);
    }
}
