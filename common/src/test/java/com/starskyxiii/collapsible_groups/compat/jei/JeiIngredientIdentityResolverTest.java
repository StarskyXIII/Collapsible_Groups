package com.starskyxiii.collapsible_groups.compat.jei;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@SuppressWarnings("removal")
class JeiIngredientIdentityResolverTest {
	@Test
	void typedUidIsTheRuntimeKeyInsteadOfTheValueOverload() {
		Object rawUid = new Object() {
			@Override public String toString() { return "typed-uid"; }
		};
		IIngredientType<String> type = type();
		IIngredientHelper<String> helper = helper(type, rawUid);

		JeiIngredientIdentityResolver.ResolvedUid resolved =
			JeiIngredientIdentityResolver.resolve(helper, typed(type, "value"));

		assertSame(rawUid, resolved.runtimeKey());
		assertEquals("typed-uid", resolved.valueId());
	}

	@Test
	void unusableUidStringsFallBackWithoutReplacingRuntimeEquality() {
		IIngredientType<String> type = type();
		List<Object> rawUids = List.of(
			new Object() { @Override public String toString() { return null; } },
			new Object() { @Override public String toString() { return "   "; } },
			new Object() { @Override public String toString() { throw new IllegalStateException("broken"); } }
		);
		for (Object rawUid : rawUids) {
			JeiIngredientIdentityResolver.ResolvedUid resolved =
				JeiIngredientIdentityResolver.resolve(helper(type, rawUid), typed(type, "value"));
			assertSame(rawUid, resolved.runtimeKey());
			assertEquals("test:value", resolved.valueId());
		}
	}

	private static IIngredientType<String> type() {
		return new IIngredientType<>() {
			@Override public Class<? extends String> getIngredientClass() { return String.class; }
			@Override public String getUid() { return "test:type"; }
		};
	}

	private static IIngredientHelper<String> helper(IIngredientType<String> type, Object typedUid) {
		return new IIngredientHelper<>() {
			@Override public IIngredientType<String> getIngredientType() { return type; }
			@Override public String getDisplayName(String ingredient) { return ingredient; }
			@Override public String getUniqueId(String ingredient, UidContext context) { return "legacy:" + ingredient; }
			@Override public Object getUid(String ingredient, UidContext context) { return "value-overload"; }
			@Override public Object getUid(ITypedIngredient<String> ingredient, UidContext context) { return typedUid; }
			@Override public ResourceLocation getResourceLocation(String ingredient) {
				return ResourceLocation.fromNamespaceAndPath("test", ingredient);
			}
			@Override public String copyIngredient(String ingredient) { return ingredient; }
			@Override public String getErrorInfo(String ingredient) { return ingredient; }
		};
	}

	private static ITypedIngredient<String> typed(IIngredientType<String> type, String value) {
		return new ITypedIngredient<>() {
			@Override public IIngredientType<String> getType() { return type; }
			@Override public String getIngredient() { return value; }
		};
	}
}
