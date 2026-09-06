package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditorValuePickerKindTest {
	@Test void idAndTagDispatchUseIndependentRuntimeApis() {
		List<String> calls = new ArrayList<>();
		var runtime = (EditorRuntimeAccess) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {EditorRuntimeAccess.class},
			(proxy, method, args) -> {
				calls.add(method.getName());
				return switch (method.getName()) {
					case "ingredientIds" -> EditorIngredientIds.UNAVAILABLE;
					case "ingredientTags" -> EditorIngredientTags.UNAVAILABLE;
					case "updateIngredientIds", "updateIngredientTags", "cancelIngredientIds", "cancelIngredientTags" -> null;
					default -> throw new AssertionError(method.getName());
				};
			});
		for (var kind : EditorValuePickerKind.values()) {
			calls.clear();
			kind.update(runtime, "test:chemical");
			var snapshot = kind.snapshot(runtime, "test:chemical");
			kind.cancel(runtime);
			String suffix = kind == EditorValuePickerKind.ID ? "Ids" : "Tags";
			assertEquals(List.of("updateIngredient" + suffix, "ingredient" + suffix, "cancelIngredient" + suffix), calls);
			assertEquals(kind, snapshot.kind());
		}
		assertEquals(EditorValuePickerKind.ID, EditorValuePickerKind.forNode(GroupFilterRuleDraft.NodeKind.ID));
		assertEquals(EditorValuePickerKind.TAG, EditorValuePickerKind.forNode(GroupFilterRuleDraft.NodeKind.TAG));
	}

	@Test void tagCoverageAndPartialMessagesRemainDistinct() {
		var registry = EditorValuePickerKind.fromTags(new EditorIngredientTags(new Object(), "test:chemical", EditorIngredientTags.Status.READY,
			EditorIngredientTags.Coverage.REGISTRY_BACKED, false, List.of("c:a")));
		var observed = EditorValuePickerKind.fromTags(new EditorIngredientTags(new Object(), "test:chemical", EditorIngredientTags.Status.READY,
			EditorIngredientTags.Coverage.OBSERVED_ONLY, false, List.of("c:a")));
		var partial = EditorValuePickerKind.fromTags(new EditorIngredientTags(new Object(), "test:chemical", EditorIngredientTags.Status.READY,
			EditorIngredientTags.Coverage.REGISTRY_BACKED, true, List.of("c:a")));
		assertEquals(ModTranslationKeys.EDITOR_RULES_TAG_REGISTRY, registry.statusKey());
		assertEquals(ModTranslationKeys.EDITOR_RULES_TAG_OBSERVED, observed.statusKey());
		assertEquals(ModTranslationKeys.EDITOR_RULES_TAG_PARTIAL, partial.statusKey());
		assertTrue(partial.ready());
		assertEquals(List.of("c:a"), partial.values());
	}

	@Test void pendingUnavailableAndMissingTypesNeverEnableConfirmation() {
		for (var status : EditorIngredientTags.Status.values()) {
			if (status == EditorIngredientTags.Status.READY) continue;
			assertFalse(EditorValuePickerKind.fromTags(new EditorIngredientTags(new Object(), "test:chemical", status,
				EditorIngredientTags.Coverage.OBSERVED_ONLY, true, List.of())).ready());
		}
		for (var status : EditorIngredientIds.Status.values()) {
			if (status == EditorIngredientIds.Status.READY) continue;
			assertFalse(EditorValuePickerKind.fromIds(new EditorIngredientIds(new Object(), "test:chemical", status, true, List.of())).ready());
		}
	}

	@Test void partialIdsRemainSelectableAndExplainVariantMatching() {
		var snapshot = EditorValuePickerKind.fromIds(new EditorIngredientIds(new Object(), "test:chemical",
			EditorIngredientIds.Status.READY, true, List.of("test:oxygen")));
		assertTrue(snapshot.ready());
		assertEquals(ModTranslationKeys.EDITOR_RULES_ID_PARTIAL, snapshot.statusKey());
		assertEquals(List.of("test:oxygen"), snapshot.values());
	}
}
