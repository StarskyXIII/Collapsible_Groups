package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Converts JEI's opaque UID into separate runtime and persistent identity values. */
public final class JeiIngredientIdentityResolver {
	private JeiIngredientIdentityResolver() {}

	public static <T> ResolvedUid resolve(IIngredientHelper<T> helper, ITypedIngredient<T> typed) {
		T ingredient = typed.getIngredient();
		Object uid = helper.getUid(typed, UidContext.Ingredient);
		if (uid != null) {
			String valueId = safeUidString(uid);
			if (valueId != null) return new ResolvedUid(uid, valueId);
		}
		String fallback = fallbackValueId(helper, ingredient);
		return new ResolvedUid(uid == null ? fallback : uid, fallback);
	}

	public static <T> ResolvedUid fallback(IIngredientHelper<T> helper, @Nullable T ingredient) {
		String fallback = fallbackValueId(helper, ingredient);
		return new ResolvedUid(fallback, fallback);
	}

	@Nullable
	private static String safeUidString(Object uid) {
		try {
			String value = uid.toString();
			return value == null || value.isBlank() ? null : value;
		} catch (RuntimeException | LinkageError ignored) {
			return null;
		}
	}

	private static <T> String fallbackValueId(IIngredientHelper<T> helper, @Nullable T ingredient) {
		try {
			Identifier id = helper.getIdentifier(ingredient);
			if (id != null && !id.toString().isBlank()) return id.toString();
		} catch (RuntimeException | LinkageError ignored) {
			// Try the helper's diagnostic representation next.
		}
		try {
			String errorInfo = helper.getErrorInfo(ingredient);
			if (errorInfo != null && !errorInfo.isBlank()) return errorInfo;
		} catch (RuntimeException | LinkageError ignored) {
			// Fall through to a guaranteed non-blank diagnostic value.
		}
		return ingredient == null ? "unknown-ingredient" : ingredient.getClass().getName();
	}

	public record ResolvedUid(Object runtimeKey, String valueId) {
		public ResolvedUid {
			Objects.requireNonNull(runtimeKey, "runtimeKey");
			Objects.requireNonNull(valueId, "valueId");
			if (valueId.isBlank()) throw new IllegalArgumentException("valueId must not be blank");
		}

		public ViewerIngredientIdentity identity(String typeId) {
			return new ViewerIngredientIdentity(typeId, valueId, runtimeKey);
		}
	}
}
