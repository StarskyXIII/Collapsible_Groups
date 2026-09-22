package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.Constants;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import dev.latvian.mods.rhino.Context;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class KubeJSCompatibility {
	private static final Detection DETECTION = detect(name -> Class.forName(name, false, KubeJSCompatibility.class.getClassLoader()));
	private static boolean warned;

	private KubeJSCompatibility() {}

	public static synchronized boolean isSupported() {
		if (DETECTION.api() != null) return true;
		if (!warned) {
			warned = true;
			String version = ModList.get().getModContainerById("kubejs")
				.map(mod -> mod.getModInfo().getVersion().toString()).orElse("unknown");
			Constants.LOG.warn("KubeJS {} group integration disabled: {}. JSON groups remain available. Verified KubeJS builds: 181, 353, 377.",
				version, DETECTION.failure());
		}
		return false;
	}

	static Detection detect(ClassLookup lookup) {
		StringBuilder failures = new StringBuilder();
		for (boolean legacy : new boolean[]{false, true}) {
			try {
				Class<?> events = lookup.load(legacy ? "dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEvents"
					: "dev.latvian.mods.kubejs.plugin.builtin.event.RecipeViewerEvents");
				Field groups = events.getField("GROUP_ENTRIES");
				if (!Modifier.isStatic(groups.getModifiers()) || groups.getType() != TargetedEventHandler.class) {
					throw new NoSuchFieldException(events.getName() + ".GROUP_ENTRIES: TargetedEventHandler");
				}
				Class<?> tags = lookup.load(legacy ? "dev.latvian.mods.kubejs.bindings.IngredientWrapper"
					: "dev.latvian.mods.kubejs.plugin.builtin.wrapper.IngredientWrapper");
				Class<?> items = legacy ? lookup.load("dev.latvian.mods.kubejs.item.ingredient.IngredientJS") : tags;
				Class<?> context = legacy ? lookup.load("dev.latvian.mods.kubejs.util.RegistryAccessContainer") : Context.class;
				Method registry = legacy ? method(context, "of", context, Context.class) : null;
				return new Detection(new Api(groups,
					method(items, "wrap", Ingredient.class, context, Object.class),
					method(tags, "tagKeyOf", TagKey.class, Ingredient.class),
					method(tags, "containsAnyTag", boolean.class, Ingredient.class),
					method(lookup.load("dev.latvian.mods.kubejs.fluid.FluidWrapper"), "wrapIngredient", FluidIngredient.class, context, Object.class),
					registry), null);
			} catch (ReflectiveOperationException | LinkageError failure) {
				if (!failures.isEmpty()) failures.append("; ");
				failures.append(failure);
			}
		}
		return new Detection(null, failures.toString());
	}

	private static Method method(Class<?> owner, String name, Class<?> result, Class<?>... arguments) throws NoSuchMethodException {
		Method method = owner.getMethod(name, arguments);
		if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != result) {
			throw new NoSuchMethodException(owner.getName() + "." + name + ": " + result.getName());
		}
		return method;
	}

	private static Api api() {
		if (DETECTION.api() == null) throw new IllegalStateException(DETECTION.failure());
		return DETECTION.api();
	}

	@SuppressWarnings("unchecked")
	static TargetedEventHandler<RecipeViewerEntryType> groupEntries() {
		try {
			return (TargetedEventHandler<RecipeViewerEntryType>) api().groups().get(null);
		} catch (IllegalAccessException exception) {
			throw new IllegalStateException(exception);
		}
	}

	static Ingredient wrapItem(Context context, Object value) {
		return (Ingredient) invoke(api().items(), registry(context), value);
	}

	@SuppressWarnings("unchecked")
	static TagKey<Item> tagKeyOf(Ingredient ingredient) {
		return (TagKey<Item>) invoke(api().tag(), ingredient);
	}

	static boolean containsAnyTag(Ingredient ingredient) {
		return (boolean) invoke(api().containsTag(), ingredient);
	}

	static FluidIngredient wrapFluid(Context context, Object value) {
		return (FluidIngredient) invoke(api().fluids(), registry(context), value);
	}

	private static Object registry(Context context) {
		return api().registry() == null ? context : invoke(api().registry(), context);
	}

	private static Object invoke(Method method, Object... arguments) {
		try {
			return method.invoke(null, arguments);
		} catch (InvocationTargetException exception) {
			if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
			if (exception.getCause() instanceof Error error) throw error;
			throw new IllegalStateException(exception.getCause());
		} catch (IllegalAccessException exception) {
			throw new IllegalStateException(exception);
		}
	}

	@FunctionalInterface
	interface ClassLookup {
		Class<?> load(String name) throws ClassNotFoundException;
	}

	record Detection(Api api, String failure) {}
	record Api(Field groups, Method items, Method tag, Method containsTag, Method fluids, Method registry) {}
}
