package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterValidator;
import com.starskyxiii.collapsible_groups.ingredient.TagQueryDiagnostics;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class EditorTagDiagnosticsTest {
	@Test void sameDraftUpdatesWarningsWhenRuntimeCapabilityChangesWithoutInvalidatingSyntax() {
		var filter = new GroupFilter.Tag("chemical", "test:tag");
		var draft = GroupFilterRuleDraft.decode(filter);
		var node = draft.flatten().getFirst().node();
		var diagnostic = new AtomicReference<>(TagQueryDiagnostics.PENDING);
		var runtime = (EditorRuntimeAccess) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{EditorRuntimeAccess.class}, (proxy, method, args) -> {
				if (method.getName().equals("tagDiagnostics")) return diagnostic.get();
				throw new AssertionError(method.getName());
			});
		assertEquals(ModTranslationKeys.EDITOR_TAG_PENDING, EditorTagDiagnostics.warning(node, runtime));
		diagnostic.set(new TagQueryDiagnostics(TagQueryDiagnostics.Availability.UNAVAILABLE, TagQueryDiagnostics.Existence.UNKNOWN));
		assertEquals(ModTranslationKeys.EDITOR_TAG_UNAVAILABLE, EditorTagDiagnostics.warning(node, runtime));
		diagnostic.set(new TagQueryDiagnostics(TagQueryDiagnostics.Availability.PARTIAL, TagQueryDiagnostics.Existence.UNKNOWN));
		assertEquals(ModTranslationKeys.EDITOR_TAG_PARTIAL, EditorTagDiagnostics.warning(node, runtime));
		diagnostic.set(new TagQueryDiagnostics(TagQueryDiagnostics.Availability.SUPPORTED, TagQueryDiagnostics.Existence.ABSENT));
		assertEquals(ModTranslationKeys.EDITOR_RULES_UNRESOLVED_ROW, EditorTagDiagnostics.warning(node, runtime));
		diagnostic.set(new TagQueryDiagnostics(TagQueryDiagnostics.Availability.SUPPORTED, TagQueryDiagnostics.Existence.PRESENT));
		assertNull(EditorTagDiagnostics.warning(node, runtime));
		assertEquals(filter, draft.toFilter().orElseThrow());
		assertTrue(GroupFilterValidator.validate(filter).isEmpty());
	}
}
