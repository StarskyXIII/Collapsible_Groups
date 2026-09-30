package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditorInvalidPreviewTest {
 @Test void invalidIdKeepsItemsFluidsAndGenericOnTheSameLastValidFilter() {
  var filter = Filters.any(Filters.itemId("minecraft:stone"), Filters.fluidId("minecraft:water"));
  var state = new GroupEditorState(new GroupDefinition("preview_test", "Preview test", true, filter));
  var panel = new EditorRightPanel(state, () -> {});
  var calls = new ArrayList<String>();
  var runtime = (EditorRuntimeAccess) Proxy.newProxyInstance(getClass().getClassLoader(),
   new Class<?>[] {EditorRuntimeAccess.class}, (proxy, method, args) -> {
    switch (method.getName()) {
     case "beginTrace": return 0L;
     case "verifyItemIndex": return false;
     case "logIfSlow": return null;
     case "resolvePreviewItems", "resolveItems", "resolveFluids", "resolveGenericIngredients":
      assertEquals(filter, ((GroupDefinition) args[0]).filter());
      calls.add(method.getName());
      return List.of();
     default: throw new AssertionError(method);
    }
   });
  panel.rebuild(runtime);
  assertTrue(calls.contains("resolvePreviewItems"));
  var node = state.flattenedRuleNodes().stream().map(f -> f.node())
   .filter(n -> n.primaryValue().equals("minecraft:stone")).findFirst().orElseThrow();
  node.setPrimaryValue("minecraft:stone!");
  state.markRulesChanged();
  calls.clear();
  panel.rebuild(runtime);
  assertEquals(List.of("resolveItems", "resolveFluids", "resolveGenericIngredients"), calls);
  assertFalse(state.currentValidationErrors().isEmpty());
  node.setPrimaryValue("minecraft:stone");
  state.markRulesChanged();
  calls.clear();
  panel.rebuild(runtime);
  assertTrue(calls.contains("resolvePreviewItems"));
  assertFalse(calls.contains("resolveItems"));
 }
}
