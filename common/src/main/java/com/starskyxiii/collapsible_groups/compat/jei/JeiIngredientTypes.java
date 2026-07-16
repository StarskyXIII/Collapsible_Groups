package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import mezz.jei.api.ingredients.IIngredientType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * JEI-specific ingredient type mappings keyed by viewer-neutral canonical IDs.
 * Type-object identity associates discovered UIDs with explicit registrations and prevents
 * equality implementations from accidentally merging distinct JEI types.
 */
public final class JeiIngredientTypes {
	private static final Map<String, IIngredientType<?>> TYPES = new LinkedHashMap<>();
	private static final IdentityHashMap<IIngredientType<?>, String> CANONICAL_BY_TYPE = new IdentityHashMap<>();

	private JeiIngredientTypes() {}

	public static synchronized void register(String id, IIngredientType<?> type) {
		Objects.requireNonNull(type, "type");
		String existingForType = CANONICAL_BY_TYPE.get(type);
		if (existingForType != null && !existingForType.equals(id)) {
			if (IngredientTypeIds.getCanonicalOrigin(existingForType)
				== IngredientTypeIds.RegistrationOrigin.DISCOVERED) {
				IngredientTypeIds.replaceDiscoveredCanonical(existingForType, id);
				TYPES.remove(existingForType);
				TYPES.put(id, type);
				CANONICAL_BY_TYPE.put(type, id);
				return;
			}
			throw new IllegalArgumentException(
				"JEI ingredient type is already explicitly registered as '" + existingForType + "'."
			);
		}
		IIngredientType<?> existingForId = getDirect(id);
		if (existingForId != null && existingForId != type) {
			throw new IllegalArgumentException("Ingredient type ID '" + id + "' is already bound to another JEI type.");
		}
		IngredientTypeIds.registerCanonical(id, IngredientTypeIds.RegistrationOrigin.EXPLICIT);
		TYPES.put(id, type);
		CANONICAL_BY_TYPE.put(type, id);
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
		return CANONICAL_BY_TYPE.get(type);
	}

	@Nullable
	public static synchronized IngredientTypeIds.RegistrationOrigin getRegistrationOrigin(IIngredientType<?> type) {
		String canonicalId = CANONICAL_BY_TYPE.get(type);
		return canonicalId == null ? null : IngredientTypeIds.getCanonicalOrigin(canonicalId);
	}

	static synchronized boolean registerDiscovered(String uid, IIngredientType<?> type) {
		Objects.requireNonNull(type, "type");
		String existingForType = CANONICAL_BY_TYPE.get(type);
		if (existingForType != null) {
			if (!uid.equals(existingForType)) {
				IngredientTypeIds.registerAlias(uid, existingForType, IngredientTypeIds.RegistrationOrigin.DISCOVERED);
			}
			return false;
		}
		String occupiedCanonical = IngredientTypeIds.getCanonicalId(uid);
		if (occupiedCanonical != null) {
			IIngredientType<?> occupiedType = TYPES.get(occupiedCanonical);
			if (occupiedType != type) return false;
			CANONICAL_BY_TYPE.put(type, occupiedCanonical);
			return false;
		}
		IngredientTypeIds.registerCanonical(uid, IngredientTypeIds.RegistrationOrigin.DISCOVERED);
		TYPES.put(uid, type);
		CANONICAL_BY_TYPE.put(type, uid);
		return true;
	}

	static synchronized void clearDiscovered() {
		var removedCanonicalIds = IngredientTypeIds.clearDiscovered();
		removedCanonicalIds.forEach(TYPES::remove);
		CANONICAL_BY_TYPE.entrySet().removeIf(entry -> removedCanonicalIds.contains(entry.getValue()));
	}

	@Nullable
	static synchronized IIngredientType<?> getDirect(String canonicalId) {
		return TYPES.get(canonicalId);
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
