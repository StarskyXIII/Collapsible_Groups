package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditorValuePickerKindTest {
	@Test void namespacePendingBecomesReadyAndNeverSharesIdSelection() {
		var source = new EditorIngredientIds(new Object(), "type:chemical", EditorIngredientIds.Status.READY, false,
			List.of("resource:oxygen", "resource:hydrogen"));
		var projection = new EditorNamespaceCatalog();
		var selection = new EditorValueSelection();
		assertTrue(selection.update(EditorValuePickerKind.fromNamespaces(projection.snapshot(source))));
		assertFalse(selection.catalog().ready());
		assertFalse(selection.update(EditorValuePickerKind.fromNamespaces(projection.snapshot(source))));
		projection.update(source);
		assertTrue(selection.update(EditorValuePickerKind.fromNamespaces(projection.snapshot(source))));
		assertEquals(List.of("resource"), selection.rows());
		selection.select(0);
		assertFalse(selection.update(EditorValuePickerKind.fromNamespaces(projection.snapshot(source))));
		assertEquals("resource", selection.selected());
		assertTrue(selection.update(EditorValuePickerKind.fromIds(source)));
		assertNull(selection.selected());
		assertTrue(selection.update(EditorValuePickerKind.fromNamespaces(projection.snapshot(source))));
		selection.select(0);
		var reloaded = new EditorIngredientIds(new Object(), source.type(), source.status(), false, List.of("new:oxygen"));
		assertTrue(selection.update(EditorValuePickerKind.fromNamespaces(projection.snapshot(reloaded))));
		assertNull(selection.selected());
		assertTrue(selection.rows().isEmpty());
	}

	@Test void namespaceStatusesUseTheirOwnMessagesAndRetainManualFallback() {
		var projection = new EditorNamespaceCatalog();
		for (var status : EditorIngredientIds.Status.values()) {
			var source = new EditorIngredientIds(new Object(), "type", status, true, List.of("test:one"));
			projection.update(source);
			var snapshot = EditorValuePickerKind.fromNamespaces(projection.snapshot(source));
			assertEquals(status == EditorIngredientIds.Status.READY, snapshot.ready());
			assertEquals(switch (status) {
				case READY -> ModTranslationKeys.EDITOR_RULES_NAMESPACE_PARTIAL;
				case PENDING -> ModTranslationKeys.EDITOR_RULES_NAMESPACE_PENDING;
				case UNAVAILABLE -> ModTranslationKeys.EDITOR_RULES_NAMESPACE_UNAVAILABLE;
				case TYPE_MISSING -> ModTranslationKeys.EDITOR_RULES_TYPE_MISSING;
			}, snapshot.statusKey());
		}
		assertEquals(EditorValuePickerKind.NAMESPACE, EditorValuePickerKind.forNode(GroupFilterRuleDraft.NodeKind.NAMESPACE));
	}

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
			String suffix = kind == EditorValuePickerKind.TAG ? "Tags" : "Ids";
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
