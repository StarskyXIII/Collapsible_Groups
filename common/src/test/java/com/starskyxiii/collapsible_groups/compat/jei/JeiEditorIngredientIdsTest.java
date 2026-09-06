package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.client.editor.EditorIdCatalog;
import com.starskyxiii.collapsible_groups.client.editor.EditorIngredientIds;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GenericJeiIngredientView;
import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;
import mezz.jei.api.ingredients.IIngredientHelper;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JeiEditorIngredientIdsTest {
	@Test void pickerUsesTheSameResourceIdAndUidFallbackAsTheJeiFilterView() {
		for (boolean useResource : List.of(true, false)) {
			AtomicInteger uidCalls = new AtomicInteger();
			@SuppressWarnings("unchecked")
			var helper = (IIngredientHelper<String>) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] {IIngredientHelper.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getResourceLocation" -> useResource ? ResourceLocation.parse("test:oxygen") : null;
					case "getUid" -> { uidCalls.incrementAndGet(); yield "test:uid_fallback"; }
					default -> throw new AssertionError(method.getName());
				});
			var view = new GenericJeiIngredientView<>("test:chemical", "oxygen", helper);
			var entry = new ViewerIngredient<>(new ViewerIngredientIdentity("test:chemical", "different:identity"),
				ViewerIngredient.Kind.GENERIC, "oxygen", view);
			var type = new ViewerIngredientType<>("test:chemical", List.of("test:alias"), List.of(entry));
			Object generation = new Object();
			var catalog = new EditorIdCatalog(16);
			for (int i = 0; i < 10; i++) catalog.update(generation, type.canonicalId(), EditorIngredientIds.Status.READY,
				() -> EditorIngredientIds.sources(type), () -> true);
			String expected = useResource ? "test:oxygen" : "test:uid_fallback";
			assertEquals(List.of(expected), catalog.snapshot(generation, type.canonicalId()).ids());
			assertEquals(useResource ? 0 : 1, uidCalls.get());
			assertTrue(CompiledFilter.compile(Filters.id(type.canonicalId(), expected)).matches(view));
		}
	}
}
