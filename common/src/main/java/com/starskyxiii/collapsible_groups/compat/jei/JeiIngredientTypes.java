package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import mezz.jei.api.ingredients.IIngredientType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

/** JEI-specific ingredient type mappings keyed by viewer-neutral canonical IDs. */
public final class JeiIngredientTypes {
	private static final Map<String, IIngredientType<?>> TYPES = new LinkedHashMap<>();

	private JeiIngredientTypes() {}

	public static synchronized void register(String id, IIngredientType<?> type) {
		IngredientTypeIds.registerCanonical(id);
		TYPES.put(id, type);
	}

	public static void registerAlias(String alias, String canonicalId) {
		IngredientTypeIds.registerAlias(alias, canonicalId);
	}

	@Nullable
	public static synchronized IIngredientType<?> get(String id) {
		String canonicalId = IngredientTypeIds.getCanonicalId(id);
		return canonicalId == null ? null : TYPES.get(canonicalId);
	}

	@Nullable
	public static synchronized String getCanonicalId(IIngredientType<?> type) {
		for (Map.Entry<String, IIngredientType<?>> entry : TYPES.entrySet()) {
			if (entry.getValue().equals(type)) return entry.getKey();
		}
		return null;
	}

	public static synchronized Map<String, IIngredientType<?>> getAll() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(TYPES));
	}

	public static synchronized Map<String, IIngredientType<?>> getAllWithAliases() {
		Map<String, IIngredientType<?>> all = new LinkedHashMap<>();
		IngredientTypeIds.getAllIds().forEach((id, canonicalId) -> {
			IIngredientType<?> type = TYPES.get(canonicalId);
			if (type != null) all.put(id, type);
		});
		return Collections.unmodifiableMap(all);
	}

	public static IIngredientType<?> getFluidType() {
		return FluidTypeHolder.PROVIDER.getFluidType();
	}

	public interface FluidTypeProvider {
		IIngredientType<?> getFluidType();
	}

	private static final class FluidTypeHolder {
		private static final FluidTypeProvider PROVIDER = ServiceLoader.load(FluidTypeProvider.class)
			.findFirst()
			.orElse(() -> null);
	}
}
