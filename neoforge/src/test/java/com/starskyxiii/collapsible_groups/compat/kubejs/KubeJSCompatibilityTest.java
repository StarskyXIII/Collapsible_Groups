package com.starskyxiii.collapsible_groups.compat.kubejs;

import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KubeJSCompatibilityTest {
	private static boolean eventsInitialized;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void runtimeProvidesTheEntireSupportedShape() {
		var detection = KubeJSCompatibility.detect(KubeJSCompatibilityTest::load);
		assertNotNull(detection.api(), detection.failure());
		assertTrue(KubeJSCompatibility.isSupported());
		String expected = System.getProperty("cg.test.kubejsVersion", "");
		if (!expected.isEmpty()) {
			assertTrue(detection.api().items().getDeclaringClass().getProtectionDomain()
				.getCodeSource().getLocation().toString().contains(expected));
		}
	}

	@Test
	void resolvesNativeItemFluidAndTagInputs() {
		Context context = new ContextFactory().enter();
		assertTrue(KubeJSCompatibility.wrapItem(context, "minecraft:stone").test(Items.STONE.getDefaultInstance()));
		assertTrue(KubeJSCompatibility.wrapFluid(context, "minecraft:water").test(new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 1000)));
		var tag = TagKey.create(Registries.ITEM, ResourceLocation.parse("minecraft:logs"));
		Ingredient ingredient = Ingredient.of(tag);
		assertEquals(tag, KubeJSCompatibility.tagKeyOf(ingredient));
		assertTrue(KubeJSCompatibility.containsAnyTag(ingredient));
		assertFalse(KubeJSCompatibility.containsAnyTag(Ingredient.of(Items.STONE)));
		assertNotNull(KubeJSCompatibility.wrapItem(context, "@minecraft"));
		assertNotNull(KubeJSCompatibility.wrapFluid(context, "#minecraft:water"));
	}

	@Test
	void probingDoesNotInitializeTheEventRegistry() throws Exception {
		var detection = KubeJSCompatibility.detect(name -> name.endsWith(".RecipeViewerEvents")
			? load(KubeJSCompatibilityTest.class.getName() + "$DeferredEvents") : load(name));
		assertNotNull(detection.api(), detection.failure());
		assertFalse(eventsInitialized);
		detection.api().groups().get(null);
		assertTrue(eventsInitialized);
	}

	@Test
	void missingWrapperDisablesTheWholeShape() {
		var detection = KubeJSCompatibility.detect(name -> {
			if (name.endsWith(".IngredientWrapper")) return Object.class;
			return load(name);
		});
		assertNull(detection.api());
		assertNotNull(detection.failure());
	}

	@Test
	void missingEventClassDoesNotProduceANoopHandler() {
		var detection = KubeJSCompatibility.detect(name -> {
			if (name.endsWith(".RecipeViewerEvents")) throw new ClassNotFoundException(name);
			return load(name);
		});
		assertNull(detection.api());
		assertTrue(detection.failure().contains("RecipeViewerEvents"));
	}

	private static Class<?> load(String name) throws ClassNotFoundException {
		return Class.forName(name, false, KubeJSCompatibilityTest.class.getClassLoader());
	}

	public static final class DeferredEvents {
		public static final TargetedEventHandler<RecipeViewerEntryType> GROUP_ENTRIES;
		static {
			eventsInitialized = true;
			GROUP_ENTRIES = null;
		}
	}
}
