package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditorPreviewRoutingTest {
	@Test void defaultRuntimePreservesFlatAndHybridRoutes() {
		List<String> calls = new ArrayList<>();
		var group = new GroupDefinition("test", "Test", false, Filters.itemId("minecraft:stone"));
		var draft = GroupFilterEditorDraft.decode(group.filter()).draft();
		var runtime = (EditorRuntimeAccess) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{EditorRuntimeAccess.class}, (proxy, method, args) -> {
				if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
				calls.add(method.getName());
				assertSame(draft, args[0]);
				assertEquals(false, args[1]);
				return List.of();
			});
		assertTrue(runtime.resolvePreviewItems(group, draft, true).isEmpty());
		assertTrue(runtime.resolvePreviewItems(group, draft, false).isEmpty());
		assertEquals(List.of("resolveEditorDraftItems", "resolveHybridEditorDraftItems"), calls);
	}
}
