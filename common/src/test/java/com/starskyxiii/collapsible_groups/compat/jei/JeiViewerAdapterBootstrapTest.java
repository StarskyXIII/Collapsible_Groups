package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerProjection;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SuppressWarnings("removal")
class JeiViewerAdapterBootstrapTest {
	@Test
	@SuppressWarnings("unchecked")
	void customTypeProjectsFromBootstrapBeforeRuntimeIsAvailable() {
		IIngredientType<String> type = () -> String.class;
		JeiIngredientTypes.register("test:bootstrap_chemical", type);
		JeiIngredientTypes.registerAlias("bootstrap_chemical", "test:bootstrap_chemical");
		IIngredientHelper<String> helper = helper(type);
		ITypedIngredient<String> oxygen = typed(type, "oxygen");
		ITypedIngredient<String> hydrogen = typed(type, "hydrogen");
		List<ITypedIngredient<?>> all = List.of(oxygen, hydrogen);
		IIngredientManager manager = (IIngredientManager) Proxy.newProxyInstance(
			getClass().getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> {
				if (method.getName().equals("getIngredientHelper")) return helper;
				if (method.getName().equals("getAllTypedIngredients")) return all;
				throw new UnsupportedOperationException(method.toString());
			}
		);
		GroupDefinition group = new GroupDefinition(
			"__kjs_test_bootstrap",
			"Bootstrap chemicals",
			true,
			Filters.genericNamespace("test:bootstrap_chemical", "test")
		);

		JeiRuntimeHolder.set(null);
		JeiViewerAdapter adapter = JeiViewerAdapter.instance();
		try {
			GroupCandidateIndex ownership =
				adapter.buildOwnershipIndex(all, manager, List.of(group));
			ViewerIngredientType<ITypedIngredient<?>> bootstrapType =
				adapter.bootstrapContext().resolveType("bootstrap_chemical").orElseThrow();
			assertEquals("test:bootstrap_chemical", bootstrapType.canonicalId());
			assertEquals(2, bootstrapType.ingredients().size());
			assertFalse(JeiRuntimeHolder.isAvailable());

			ViewerProjection<ITypedIngredient<?>> projection = adapter.project(
				all, "", false, 0, List.of(group), id -> false, ownership
			);
			ViewerProjection.GroupHeader<ITypedIngredient<?>> header = assertInstanceOf(
				ViewerProjection.GroupHeader.class,
				projection.entries().getFirst()
			);
			assertEquals("__kjs_test_bootstrap", header.group().id());
			assertEquals(List.of(oxygen, hydrogen), header.children().stream()
				.map(ingredient -> ingredient.entry())
				.toList());
		} finally {
			JeiViewerAdapter.unregisterRuntime();
			JeiRuntimeHolder.set(null);
		}
	}

	private static IIngredientHelper<String> helper(IIngredientType<String> type) {
		return new IIngredientHelper<>() {
			@Override
			public IIngredientType<String> getIngredientType() {
				return type;
			}

			@Override
			public String getDisplayName(String ingredient) {
				return ingredient;
			}

			@Override
			public String getUid(String ingredient, UidContext context) {
				return "test:" + ingredient;
			}

			@Override
			public Identifier getIdentifier(String ingredient) {
				return Identifier.parse("test:" + ingredient);
			}

			@Override
			public String copyIngredient(String ingredient) {
				return ingredient;
			}

			@Override
			public String getErrorInfo(String ingredient) {
				return ingredient;
			}
		};
	}

	private static ITypedIngredient<String> typed(IIngredientType<String> type, String value) {
		return new ITypedIngredient<>() {
			@Override
			public IIngredientType<String> getType() {
				return type;
			}

			@Override
			public String getIngredient() {
				return value;
			}
		};
	}
}
