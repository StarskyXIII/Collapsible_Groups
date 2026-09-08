package com.starskyxiii.collapsible_groups.compat.jei;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("removal")
class JeiIngredientIdentityResolverTest {
	private static final String THROWING_UID = "throwing-uid";

	@Test
	void uniqueIdIsTheRuntimeAndPersistentKey() {
		String rawUid = "typed-uid";
		IIngredientType<String> type = type();
		IIngredientHelper<String> helper = helper(type, rawUid);

		JeiIngredientIdentityResolver.ResolvedUid resolved =
			JeiIngredientIdentityResolver.resolve(helper, typed(type, "value"));

		assertEquals(rawUid, resolved.runtimeKey());
		assertEquals("typed-uid", resolved.valueId());
	}

	@Test
	void unusableUidStringsFallBackWithoutReplacingRuntimeEquality() {
		IIngredientType<String> type = type();
		List<String> rawUids = List.of("", "   ");
		for (String rawUid : rawUids) {
			JeiIngredientIdentityResolver.ResolvedUid resolved =
				JeiIngredientIdentityResolver.resolve(helper(type, rawUid), typed(type, "value"));
			assertEquals(rawUid, resolved.runtimeKey());
			assertEquals("test:value", resolved.valueId());
		}
	}

	@Test
	void strictResolutionNeverReplacesAMissingRawUidWithARegistryId() {
		IIngredientType<String> type = type();

		assertTrue(JeiIngredientIdentityResolver.resolveStrict(
			helper(type, null), typed(type, "value")).isEmpty());
		assertTrue(JeiIngredientIdentityResolver.resolveStrict(
			helper(type, THROWING_UID), typed(type, "value")).isEmpty());
	}

	@Test
	void strictResolutionPreservesTheRawRuntimeUid() {
		String rawUid = "component-aware-uid";
		IIngredientType<String> type = type();

		JeiIngredientIdentityResolver.ResolvedUid resolved = JeiIngredientIdentityResolver.resolveStrict(
			helper(type, rawUid), typed(type, "value")).orElseThrow();

		assertEquals(rawUid, resolved.runtimeKey());
		assertEquals("component-aware-uid", resolved.valueId());
	}

	private static IIngredientType<String> type() {
		return new IIngredientType<>() {
			@Override public Class<? extends String> getIngredientClass() { return String.class; }
			@Override public String getUid() { return "test:type"; }
		};
	}

	private static IIngredientHelper<String> helper(IIngredientType<String> type, String typedUid) {
		return new IIngredientHelper<>() {
			@Override public IIngredientType<String> getIngredientType() { return type; }
			@Override public String getDisplayName(String ingredient) { return ingredient; }
			@Override public String getUniqueId(String ingredient, UidContext context) {
				if (typedUid == THROWING_UID) throw new IllegalStateException("broken uid");
				return typedUid;
			}
			@Override public ResourceLocation getResourceLocation(String ingredient) {
				return new ResourceLocation("test", ingredient);
			}
			@Override public String copyIngredient(String ingredient) { return ingredient; }
			@Override public String getErrorInfo(String ingredient) { return ingredient; }
		};
	}

	private static ITypedIngredient<String> typed(IIngredientType<String> type, String value) {
		return new ITypedIngredient<String>() {
			@Override public IIngredientType<String> getType() { return type; }
			@Override public String getIngredient() { return value; }
			public ITypedIngredient<String> normalize(IIngredientHelper<String> helper) { return this; }
		};
	}
}
